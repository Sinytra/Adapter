package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;

/**
 * Handles {@code @Accessor} mixins, the one handler that does not target a method at all. It opts out with
 * {@link #targetsMethod()} because the method-first pipeline would mark every accessor as unadapted.
 */
public class AccessorMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = PropertyContainerTemplate.builder()
        .require(ControlKeys.MIXIN_TYPE, ControlKeys.TARGET_CLASS, ControlKeys.RETURN_TYPE, ControlKeys.PARAMETERS)
        .keys(MixinKeys.ACCESSOR_VALUE, MixinKeys.REQUIRE)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public boolean targetsMethod() {
        return false;
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        return TxResult.SUCCESS;
    }

    @Override
    public void validateNonMethodTarget(MixinContext context, Configuration config) {
        String declared = config.getProperty(MixinKeys.ACCESSOR_VALUE).orElse(null);
        final String fieldName = (declared == null || declared.isEmpty())
            ? inferFieldName(context.methodNode().name)
            : declared;

        String targetClass = config.getTargetClass();
        ClassNode target = targetClass == null ? null : context.dirtyLookup().getClass(targetClass).orElse(null);

        boolean found = target != null && fieldName != null
            && target.fields.stream().anyMatch(f -> f.name.equals(fieldName));

        context.environment().auditTrail().recordResult(context, config,
            found ? AuditTrail.Match.FULL : AuditTrail.Match.NONE);
    }

    /** Mixin infers the field from the method name when no value is given: {@code getFoo} accesses {@code foo}. */
    private static String inferFieldName(String methodName) {
        String base;
        if (methodName.startsWith("get") || methodName.startsWith("set")) {
            base = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            base = methodName.substring(2);
        } else {
            return null;
        }
        if (base.isEmpty()) {
            return null;
        }
        return Character.toLowerCase(base.charAt(0)) + base.substring(1);
    }
}
