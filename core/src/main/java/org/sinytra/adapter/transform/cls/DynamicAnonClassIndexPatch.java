package org.sinytra.adapter.transform.cls;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.PatchContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.patch.MixinParser;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.transform.ClassTransformer;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;

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
                    stripOwnerFromMixinTargets(classNode, classTarget, context, inner.name);
                    return PatchResult.APPLY;
                }
            }
        }

        return PatchResult.PASS;
    }

    private static void stripOwnerFromMixinTargets(ClassNode classNode, ClassTarget classTarget, PatchContext context, String newOwner) {
        for (MethodNode method : classNode.methods) {
            MixinParser.MixinMethodHandle handle = MixinParser.parseMixin(classTarget, method, context);
            if (handle == null) continue;

            handle.properties().getProperty(MixinKeys.TARGET_METHOD)
                .map(q -> q.withOwner(newOwner))
                .map(MixinKeys.TARGET_METHOD::serialize)
                .ifPresent(q -> handle.methodAnnotation().setOrAppendNonNull(MixinKeys.TARGET_METHOD.name(), q));
        }
    }
}
