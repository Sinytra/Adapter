package org.sinytra.adapter.patch.transformer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;

import java.util.Collection;
import java.util.Set;

public record SplitMixinTransform(String targetClass) implements MethodTransform {

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.WRAP_WITH_CONDITION, MixinConstants.WRAP_OPERATION, MixinConstants.MODIFY_CONST, MixinConstants.MODIFY_ARG, MixinConstants.INJECT, MixinConstants.REDIRECT, MixinConstants.MODIFY_VAR, MixinConstants.MODIFY_EXPR_VAL);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        MethodTransform transform = new ExtractMixin(this.targetClass, false);
        Patch.Result result = transform.apply(classNode, methodNode, methodContext, context);
        if (methodContext.targetTypes().size() > 1) {
            MethodTransform removeTarget = new ModifyTargetClasses(l -> l.remove(Type.getObjectType(this.targetClass)));
            removeTarget.apply(classNode, methodNode, methodContext, context);
        }
        return result;
    }
}
