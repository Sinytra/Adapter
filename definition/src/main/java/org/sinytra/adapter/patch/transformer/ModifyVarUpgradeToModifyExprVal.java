package org.sinytra.adapter.patch.transformer;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;

import java.util.Collection;
import java.util.Set;

/**
 * Original mixin:
 *
 * <pre>{@code @ModifyVariable(
 *     method = "exampleMethod",
 *     at = @At(
 *         value = "INVOKE",
 *         target = "someOtherMethod()I",
 *         shift = At.Shift.BY,
 *         by = 2
 *     )
 * )
 * private void someMethodMixin(int original) {
 *     return original * 2;
 * }
 * }</pre>
 * <p>
 * Original target:
 *
 * <pre>{@code
 * public int exampleMethod() {
 *     int i = someOtherMethod();
 * >>> i = localvar$zfk000$someMethodMixin(i);
 *     // ...
 * }
 * }</pre>
 * <p>
 * Patched target:
 * <pre>{@code
 * public int exampleMethod() {
 * <<< int i = someOtherMethod();
 * >>> int i = modifyExpressionValue$zfk000$someMethodMixin(someOtherMethod());
 *     // ...
 * }
 * }</pre>
 * <p>
 * Patched mixin:
 * 
 * <pre>{@code @ModifyExpressionValue(
 *     method = "exampleMethod",
 *     at = @At(
 *         value = "INVOKE",
 *         target="someOtherMethod()I"
 *     )
 * )
 * private void someMethodMixin(int original) {
 *     return original * 2;
 * }
 * }</pre>
 */
public class ModifyVarUpgradeToModifyExprVal implements MethodTransform {
    public static final ModifyVarUpgradeToModifyExprVal INSTANCE = new ModifyVarUpgradeToModifyExprVal();
    private static final Collection<String> ALLOWED_VALUES = Set.of("INVOKE", "INVOKE_ASSIGN");

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.MODIFY_VAR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle injectionAnnotation = methodContext.injectionPointAnnotation();
        // Conditions must be met:
        // - The injection target value is INVOKE
        // - The target is shifted BY 2
        if (injectionAnnotation == null || injectionAnnotation.<String>getValue("value").filter(v -> ALLOWED_VALUES.contains(v.get())).isEmpty() || injectionAnnotation.getValue("shift").isEmpty() || injectionAnnotation.getValue("by").isEmpty()) {
            return Patch.Result.PASS;
        }
        AnnotationValueHandle<String> target = injectionAnnotation.<String>getValue("target").orElse(null);
        if (target == null) {
            return null;
        }
        // Modify mixin type
        MethodTransform transform = new ModifyMixinType(MixinConstants.MODIFY_EXPR_VAL, b -> b
            .sameTarget()
            .injectionPoint("INVOKE", target.get()));
        return transform.apply(classNode, methodNode, methodContext, context);
    }
}
