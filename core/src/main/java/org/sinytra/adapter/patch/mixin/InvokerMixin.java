package org.sinytra.adapter.patch.mixin;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.AuditTrail;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.config.PropertyContainer;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.util.MethodQualifier;

/**
 * Handles {@code @Invoker} mixins. Like {@code @Accessor} an invoker is an interface mixin - Mixin generates a
 * method that calls the target rather than injecting into a body - so the injection pipeline would always
 * report a failure. It opts out with {@link #targetsMethod()} and looks the named method up instead.
 */
public class InvokerMixin implements MixinType {
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
        MethodQualifier qualifier = deriveTarget(config, context.methodNode());
        String name = qualifier == null ? null : qualifier.name();

        String targetClass = config.getTargetClass();
        ClassNode target = targetClass == null ? null : context.dirtyLookup().getClass(targetClass).orElse(null);

        boolean found = target != null && name != null
            && target.methods.stream().anyMatch(m -> m.name.equals(name));

        context.environment().auditTrail().recordResult(context, config,
            found ? AuditTrail.Match.FULL : AuditTrail.Match.NONE);
    }

    /** The invoker names its target with {@code value}; without one Mixin strips a call/invoke prefix. */
    @Nullable
    @Override
    public MethodQualifier deriveTarget(PropertyContainer properties, MethodNode method) {
        String name = properties.getProperty(MixinKeys.ACCESSOR_VALUE).orElse(null);
        if (name == null || name.isEmpty()) {
            name = method.name;
            for (String prefix : new String[]{"invoker", "invoke", "call"}) {
                if (name.startsWith(prefix) && name.length() > prefix.length()) {
                    name = name.substring(prefix.length());
                    break;
                }
            }
            if (name.isEmpty()) {
                return null;
            }
            name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        }
        return new MethodQualifier(name, null);
    }
}
