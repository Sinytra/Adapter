package org.sinytra.adapter.patch.mixin;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.Type;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.MethodParameters.ParamGroup;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.env.util.MixinAnnotationConstants;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.*;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.processor.ParametersProcessor;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.processor.redirect.DivertRedirectProcessor;
import org.sinytra.adapter.patch.processor.redirect.ParameterUsageProcessor;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.patch.resolver.injection.InjectionPointResolver;
import org.sinytra.adapter.patch.resolver.special.ResolverSyntheticInstanceof;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.sinytra.adapter.env.param.MethodParameters.ParamGroup.*;

public class RedirectMixin implements MixinType {
    private static final PropertyContainerTemplate TEMPLATE = ConfigurationTemplates.MIXIN_AT.extend()
        .pluralKeys(MixinKeys.TARGET_METHOD)
        .build();

    @Override
    public PropertyContainerTemplate getConfigurationTemplate() {
        return TEMPLATE;
    }

    @Override
    public Set<MixinFlag> getFlags() {
        return EnumSet.of(MixinFlag.AT_TARGET_SENSITIVE, MixinFlag.ACCEPTS_INSTANCE);
    }

    @Override
    public TxResult preProcess(MixinContext context, MutableConfiguration clean, Resolvers resolvers, Processors processors) {
        resolvers.
            addBefore(InjectionPointResolver.class, new ResolverSyntheticInstanceof(false));
        processors
            .addAfter(ParametersProcessor.class, new DivertRedirectProcessor())
            .addAfter(ParametersProcessor.class, new ParameterUsageProcessor());

        if (clean.getAtData() == null)
            return TxResult.FAIL;
        if (!MixinAnnotationConstants.AT_VAL_INVOKE.equals(clean.getAtData().getValue())) {
            // @Redirect is also legal for FIELD targets. Failing unconditionally dropped such mixins even
            // when the referenced member still exists, so model the handler and let the pipeline decide.
            clean.setParameters(MethodParameters.create(context.methodNode(),
                List.of(MethodParameters.ParamGroup.SINGLE_ANY)));
            clean.setReturnType(Type.getReturnType(context.methodNode().desc));
            return TxResult.SUCCESS;
        }

        MethodQualifier targetDesc = clean.getAtData().getTarget().flatMap(MethodQualifier::parse).orElse(null);
        if (targetDesc == null)
            return TxResult.FAIL;

        List<Type> methodParams = Parameters.getParameterTypes(context.methodNode().desc);
        List<Type> callTypes = Parameters.getParameterTypes(targetDesc.desc());
        boolean isStatic = isStaticRedirect(context, targetDesc, methodParams, callTypes);
        if (!isStatic) {
            callTypes.addFirst(methodParams.getFirst());
        }

        MethodParameters capturedMethodParams = MethodParameters.create(
            Parameters.parse(context.methodNode(), callTypes.size(), -1),
            List.of(ParamGroup.CAPTURED_PARAMS, ParamGroup.LOCALS)
        );

        MethodParameters params = MethodParameters.builder()
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, capturedMethodParams.getTypes(CAPTURED_PARAMS))
            .putTypes(LOCALS, capturedMethodParams.getTypes(LOCALS))
            .build();

        clean.setParameters(params);

        return TxResult.SUCCESS;
    }

    @Override
    public TxResult postProcess(MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getTargetMethod() == null)
            return TxResult.FAIL;

        // A non-INVOKE @Redirect targets a member, not a call: nothing to resolve and the ref stays valid.
        if (!MixinAnnotationConstants.AT_VAL_INVOKE.equals(dirty.getAtData().getValue()))
            return TxResult.SUCCESS;

        MethodQualifier targetDesc = dirty.getAtData().getTarget().flatMap(MethodQualifier::parse).orElse(null);
        if (targetDesc == null || targetDesc.desc() == null)
            return TxResult.FAIL;

        // Find the call inside the patched method instead of resolving the @At's owner through a class lookup:
        // NeoForge declares such members on extension interfaces, which that lookup misses.
        TargetPair injected = context.methods().findOwnMethodPair(context.dirtyLookup(), dirty.getTargetMethod());
        MethodInsnNode callInsn = null;
        if (injected != null) {
            List<MethodInsnNode> calls = new ArrayList<>();
            for (AbstractInsnNode node : context.methods().findInjectionTargetInsns(injected, dirty.getAtData())) {
                if (node instanceof MethodInsnNode mi) {
                    calls.add(mi);
                }
            }
            // Several call sites are normal; what must be unambiguous is the distinct owner+name+descriptor.
            if (calls.stream().map(c -> c.owner + c.name + c.desc).distinct().limit(2).count() == 1) {
                callInsn = calls.getFirst();
            }
        }

        TargetPair dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), targetDesc);
        if (dirtyTarget == null) {
            // Fall back to owner+name only, the same strategy ChangedMemberDescriptorSubResolver uses.
            dirtyTarget = context.methods().findOwnMethodPair(context.dirtyLookup(), targetDesc.ignoreDesc());
        }
        // Either the call or a resolvable target is enough.
        if (dirtyTarget == null && callInsn == null)
            return TxResult.FAIL;

        List<Type> dirtyCaptured = context.methods().resolveCapturedMethodParams(recipe.clean(), recipe.dirty());

        // Prefer the call: the @At may name a call whose descriptor differs from the (since changed) method
        // declaration, and Mixin binds the handler to the call.
        List<Type> callTypes = callInsn != null
            ? Parameters.getParameterTypes(callInsn.desc)
            : Parameters.getParameterTypes(targetDesc.desc());
        boolean staticCall = callInsn != null
            ? callInsn.getOpcode() == Opcodes.INVOKESTATIC
            : MethodHelper.isStatic(dirtyTarget.methodNode());
        if (!staticCall) {
            Type owner = callInsn != null
                ? Type.getObjectType(callInsn.owner)
                : Type.getObjectType(dirtyTarget.classNode().name);
            callTypes.addFirst(owner);
        }

        dirty.inheritProperyIfAbsent(ControlKeys.PARAMETERS);
        MethodParameters params = (dirty.getParameters() != null ? dirty.getParameters().mutableCopy() : MethodParameters.builder())
            .putTypes(METHOD_PARAMS, callTypes)
            .putTypes(CAPTURED_PARAMS, dirtyCaptured)
            .build();

        dirty.setParameters(params);
        dirty.setReturnType(Type.getReturnType(targetDesc.desc()));

        return TxResult.SUCCESS;
    }

    private boolean isStaticRedirect(MixinContext context, MethodQualifier targetDesc, List<Type> methodParams, List<Type> callTypes) {
        TargetPair cleanTarget = context.methods().findOwnMethodPair(context.cleanLookup(), targetDesc);
        if (cleanTarget != null) {
            return MethodHelper.isStatic(cleanTarget.methodNode());
        }
        return methodParams.size() >= callTypes.size() && methodParams.subList(0, callTypes.size()).equals(callTypes);
    }
}








