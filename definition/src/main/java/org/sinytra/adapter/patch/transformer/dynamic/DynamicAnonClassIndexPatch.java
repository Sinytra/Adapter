package org.sinytra.adapter.patch.transformer.dynamic;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

/**
 * Handle cases where a method targets an anonymous class whose index has changed.
 */
public class DynamicAnonClassIndexPatch implements ClassTransform {
    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        if (annotation == null || !annotation.getKey().equals("targets")) {
            return Patch.Result.PASS;
        }
        List<String> targets = (List<String>) annotation.get();
        if (targets.size() != 1) {
            return Patch.Result.PASS;
        }
        String theTarget = context.remap(targets.getFirst());
        if (!AdapterUtil.isAnonymousClass(theTarget)) {
            return Patch.Result.PASS;
        }

        ClassNode cleanClass = context.environment().cleanClassLookup().getClass(theTarget).orElse(null);
        if (cleanClass == null || cleanClass.outerClass == null || cleanClass.outerMethod == null) {
            return Patch.Result.PASS;
        }
        ClassNode outerDirtyClass = context.environment().dirtyClassLookup().getClass(cleanClass.outerClass).orElse(null);
        if (outerDirtyClass == null) {
            return Patch.Result.PASS;
        }
        // FIXME Cannot use remap here!
        MethodQualifier cleanOuterMethod = MethodQualifier.create(context.remap(cleanClass.outerMethod + cleanClass.outerMethodDesc)).orElse(null);
        if (cleanOuterMethod == null) {
            return Patch.Result.PASS;
        }

        for (InnerClassNode innerClass : outerDirtyClass.innerClasses) {
            if (AdapterUtil.isAnonymousClass(innerClass.name)) {
                ClassNode inner = context.environment().dirtyClassLookup().getClass(innerClass.name).orElse(null);
                if (inner == null || inner.outerMethod == null || inner.outerMethodDesc == null) {
                    continue;
                }

                if (!inner.name.equals(theTarget) && cleanOuterMethod.matches(null, inner.outerMethod, inner.outerMethodDesc)) {
                    targets.set(0, inner.name);
                    stripOwnerFromMixinTargets(classNode, context, inner.name);
                    return Patch.Result.APPLY;
                }
            }
        }

        return Patch.Result.PASS;
    }

    // TODO Improve so that we don't need to manually scan the annotations
    private static void stripOwnerFromMixinTargets(ClassNode classNode, PatchContext context, String newOwner) {
        for (MethodNode method : classNode.methods) {
            if (method.visibleAnnotations != null) {
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (PatchInstance.KNOWN_MIXIN_TYPES.contains(annotation.desc)) {
                        AnnotationHandle handle = new AnnotationHandle(annotation);
                        handle.<List<String>>getValue("method").ifPresent(val -> {
                            List<String> mapped = val.get().stream()
                                .map(target -> {
                                    String remapped = context.remap(target);
                                    MethodQualifier q = MethodQualifier.create(remapped).orElse(null);
                                    return q == null ? target : "L" + newOwner + ";" + q.name() + q.desc();
                                })
                                .toList();
                            val.set(mapped);
                        });
                    }
                }
            }
        }
    }
}
