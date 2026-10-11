package org.sinytra.adapter.patch.resolver.special;

import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.resolver.Resolver;
import org.spongepowered.asm.mixin.injection.At;
import org.sinytra.adapter.patch.config.Configurations;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE_ASSIGN;

/**
 * Upgrade a ModifyVar to a ModifyExpressionVal
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
public class ModifyVarUpgradeResolver implements Resolver {
    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        if (!recipe.hasInjectionPointValue(AT_VAL_INVOKE) && !recipe.hasInjectionPointValue(AT_VAL_INVOKE_ASSIGN))
            return ResolutionResult.pass();

        // Conditions must be met:
        // - The injection target value is INVOKE or INVOKE_ASSIGN
        // - The target is shifted BY 2
        AtData at = recipe.clean().getAtData();
        if (at == null || at.getTarget().isEmpty()) return ResolutionResult.pass();

        At.Shift shift = at.getProperty(AtData.Keys.SHIFT).orElse(null);
        if (shift != At.Shift.BY) return ResolutionResult.pass();

        boolean byTwo = at.getProperty(AtData.Keys.BY).map(i -> i == 2).orElse(false);
        if (!byTwo) return ResolutionResult.pass();

        String target = at.getTargetOrThrow();
        return ResolutionResult.replace(Configurations.changeMixinType(recipe.dirty(), MixinAnnotations.MODIFY_EXPR_VAL, AtData.create(AT_VAL_INVOKE, target)));
    }
}
