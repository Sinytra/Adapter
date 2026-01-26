package org.sinytra.adapter.next.transform.cls;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.mixin.MixinTypes;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.next.transform.ClassTransformer;
import org.sinytra.adapter.next.env.ctx.PatchContext;
import org.sinytra.adapter.next.env.ctx.PatchResult;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

/**
 * Handle cases where a method targets an anonymous class whose index has changed.
 */
public class DynamicAnonClassIndexPatch implements ClassTransformer {
    @Override
    public PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context) {
        Type singleTarget = classTarget.getSingle();
        String target = singleTarget.getInternalName();
        if (!AdapterUtil.isAnonymousClass(target)) {
            return PatchResult.PASS;
        }

        ClassNode cleanClass = context.environment().cleanClassLookup().getClass(target).orElse(null);
        if (cleanClass == null || cleanClass.outerClass == null || cleanClass.outerMethod == null) {
            return PatchResult.PASS;
        }
        ClassNode outerDirtyClass = context.environment().dirtyClassLookup().getClass(cleanClass.outerClass).orElse(null);
        if (outerDirtyClass == null) {
            return PatchResult.PASS;
        }

        // FIXME Cannot use remap here!
        MethodQualifier cleanOuterMethod = MethodQualifier.parse(context.remap(cleanClass.outerMethod + cleanClass.outerMethodDesc)).orElse(null);
        if (cleanOuterMethod == null) {
            return PatchResult.PASS;
        }

        for (InnerClassNode innerClass : outerDirtyClass.innerClasses) {
            if (AdapterUtil.isAnonymousClass(innerClass.name)) {
                ClassNode inner = context.environment().dirtyClassLookup().getClass(innerClass.name).orElse(null);
                if (inner == null || inner.outerMethod == null || inner.outerMethodDesc == null) {
                    continue;
                }

                if (!inner.name.equals(target) && cleanOuterMethod.matches(null, inner.outerMethod, inner.outerMethodDesc)) {
                    classTarget.set(Type.getObjectType(inner.name));
                    stripOwnerFromMixinTargets(classNode, context, inner.name);
                    return PatchResult.APPLY;
                }
            }
        }

        return PatchResult.PASS;
    }

    // TODO Improve so that we don't need to manually scan the annotations
    private static void stripOwnerFromMixinTargets(ClassNode classNode, PatchContext context, String newOwner) {
        for (MethodNode method : classNode.methods) {
            if (method.visibleAnnotations != null) {
                for (AnnotationNode annotation : method.visibleAnnotations) {
                    if (MixinTypes.getMixinType(annotation.desc) != null) {
                        AnnotationHandle handle = new AnnotationHandle(annotation);
                        handle.<List<String>>getValue("method").ifPresent(val -> {
                            List<String> mapped = val.get().stream()
                                .map(target -> {
                                    String remapped = context.remap(target);
                                    MethodQualifier q = MethodQualifier.parse(remapped).orElse(null);
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
