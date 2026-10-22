package org.sinytra.adapter.pipeline;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.model.param.RedirectedInsn;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class HandlerValidator {
    @Nullable
    public static String validate(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getProperty(ControlKeys.DELETE).orElse(false)
            || !Objects.equals(recipe.clean().getProperty(ControlKeys.MIXIN_TYPE), dirty.getProperty(ControlKeys.MIXIN_TYPE))
            || dirty.getTargetMethod() == null) {
            return null;
        }

        TargetPair target = recipe.getDirtyTarget();
        if (target == null) {
            return "dirty target method %s not found".formatted(dirty.getTargetMethod().asDescriptor());
        }

        AtData at = dirty.getAtData();
        List<AbstractInsnNode> insns;
        if (at != null) {
            insns = context.injections().find(target, InjectionQuery.of(at));
        } else if (dirty.hasProperty(MixinKeys.TARGET_CONSTANT)) {
            insns = context.injections().find(target, InjectionQuery.constant());
        } else {
            return null;
        }
        if (insns.isEmpty()) {
            return "the injection point selects nothing in %s".formatted(target.methodNode().name + target.methodNode().desc);
        }

        return validateSignature(context, context.getMixinType().signature(), dirty, target, insns.getFirst());
    }

    @Nullable
    private static String validateSignature(MixinContext context, HandlerSignature signature, Configuration dirty, TargetPair target, AbstractInsnNode insn) {
        MethodParameters params = dirty.getParameters();
        if (params == null) {
            return null;
        }

        Type[] handlerArgs = Type.getArgumentTypes(context.methodNode().desc);
        if (!Arrays.asList(handlerArgs).equals(params.mergeTypes())) {
            return "handler parameters %s don't match the expected %s".formatted(Arrays.toString(handlerArgs), params.mergeTypes());
        }
        Type handlerReturn = Type.getReturnType(context.methodNode().desc);
        if (!handlerReturn.equals(dirty.getReturnType())) {
            return "handler returns %s instead of the expected %s".formatted(handlerReturn, dirty.getReturnType());
        }

        RedirectedInsn redirected = RedirectedInsn.of(insn);
        List<Type> targetArgs = Parameters.getParameterTypes(target.methodNode().desc);
        Type targetReturn = Type.getReturnType(target.methodNode().desc);

        Type expectedReturn = switch (signature.returns()) {
            case VOID -> Type.VOID_TYPE;
            case CONDITION -> Type.BOOLEAN_TYPE;
            case CALL_RETURN -> redirected != null ? redirected.result() : null;
            case TARGET_RETURN -> targetReturn;
            case CALL_ARG, VARIABLE -> null;
        };
        if (expectedReturn != null && !expectedReturn.equals(handlerReturn)) {
            return "handler returns %s but the injector expects %s".formatted(handlerReturn, expectedReturn);
        }

        for (HandlerSignature.Slot slot : signature.slots()) {
            if (!params.has(slot.group())) continue;
            List<Parameter> group = params.get(slot.group());
            String problem = switch (slot.source()) {
                case CALL_OPERANDS -> redirected == null ? null : checkTypes("call operand", group, redirected.operands());
                case OPERATION -> checkTypes("operation", group, List.of(TypeConstants.OPERATION_TYPE));
                case CALLBACK_INFO -> group.isEmpty() ? null
                    : checkTypes("callback info", group, List.of(targetReturn.equals(Type.VOID_TYPE) ? TypeConstants.CI_TYPE : TypeConstants.CIR_TYPE));
                case TARGET_ARGS -> group.isEmpty() ? null : checkTypes("target argument", group, targetArgs);
                case CAPTURED_TARGET_ARGS -> group.size() > targetArgs.size() ? "handler captures %d target arguments, but the target has %d".formatted(group.size(), targetArgs.size())
                    : checkTypes("captured target argument", group, targetArgs.subList(0, group.size()));
                case VALUE -> expectedReturn == null ? null : checkTypes("value", group, List.of(expectedReturn));
                case KEEP -> null;
            };
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    @Nullable
    private static String checkTypes(String kind, List<Parameter> group, List<Type> expected) {
        if (group.size() != expected.size()) {
            return "handler has %d %s parameters, but the injector expects %d (%s)".formatted(group.size(), kind, expected.size(), expected);
        }
        for (int i = 0; i < group.size(); i++) {
            Parameter param = group.get(i);
            if (!param.type().equals(expected.get(i)) && !param.hasAnnotation(MixinAnnotations.COERCE)) {
                return "%s parameter %d is %s, but the injector expects %s".formatted(kind, i, param.type(), expected.get(i));
            }
        }
        return null;
    }

    private HandlerValidator() {
    }
}
