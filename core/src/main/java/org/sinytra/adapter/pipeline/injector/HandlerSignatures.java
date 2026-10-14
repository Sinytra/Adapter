package org.sinytra.adapter.pipeline.injector;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.constant.MixinAnnotationConstants;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.model.param.HandlerSignature;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.model.param.RedirectedInsn;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public final class HandlerSignatures {

    public static TxResult apply(MixinContext context, HandlerSignature signature, Configuration clean, MutableConfiguration dirty, Recipe recipe, @Nullable Type value) {
        Type valueType = switch (signature.returns()) {
            case VOID -> Type.VOID_TYPE;
            case CONDITION -> Type.BOOLEAN_TYPE;
            case CALL_RETURN -> {
                RedirectedInsn redirected = isInvoke(dirty) ? null : findRedirected(context, recipe, dirty);
                MethodQualifier call = getCallQualifier(dirty);
                yield redirected != null ? redirected.result() : call != null ? Type.getReturnType(call.desc()) : null;
            }
            case TARGET_RETURN -> dirty.getTargetMethod() != null && dirty.getTargetMethod().desc() != null
                ? Type.getReturnType(dirty.getTargetMethod().desc()) : null;
            case CALL_ARG, VARIABLE, CONSTANT -> value;
        };
        if (valueType == null) {
            return TxResult.FAIL;
        }

        MethodParameters.Builder params = clean.getParameters().mutableCopy();
        for (HandlerSignature.Slot slot : signature.slots()) {
            switch (slot.source()) {
                case TARGET_ARGS -> {
                    if (dirty.getTargetMethod() == null || dirty.getTargetMethod().desc() == null) return TxResult.FAIL;

                    params.putTypes(slot.group(), Parameters.getParameterTypes(dirty.getTargetMethod().desc()));
                }
                case CALL_OPERANDS -> {
                    RedirectedInsn redirected = isInvoke(dirty) ? null : findRedirected(context, recipe, dirty);
                    List<Type> operands = redirected != null ? redirected.operands() : findCallOperands(context, recipe, dirty);
                    if (operands == null) return TxResult.FAIL;

                    params.putTypes(slot.group(), operands);
                }
                case OPERATION -> params.putType(slot.group(), TypeConstants.OPERATION_TYPE);
                case VALUE -> params.putType(slot.group(), valueType);
                case CAPTURED_TARGET_ARGS -> params.putTypes(slot.group(), context.locals().findDirtyCapturedParams(recipe.clean(), recipe.dirty()));
                default -> {
                }
            }
        }

        dirty.setParameters(params.build());
        dirty.setReturnType(valueType);
        return TxResult.SUCCESS;
    }

    @Nullable
    private static MethodQualifier getCallQualifier(Configuration dirty) {
        AtData at = dirty.getAtData();
        MethodQualifier call = at != null ? at.getTargetQualifier().orElse(null) : null;
        return call != null && call.desc() != null ? call : null;
    }

    private static boolean isInvoke(Configuration dirty) {
        AtData at = dirty.getAtData();
        return at == null || MixinAnnotationConstants.AT_VAL_INVOKE.equals(at.getValue()) || MixinAnnotationConstants.AT_VAL_INVOKE_ASSIGN.equals(at.getValue());
    }

    @Nullable
    private static RedirectedInsn findRedirected(MixinContext context, Recipe recipe, Configuration dirty) {
        AbstractInsnNode insn = dirty.getAtData() != null ? context.injections().resolveFirst(recipe.getDirtyTarget(), InjectionQuery.of(dirty.getAtData())) : null;
        return insn != null ? RedirectedInsn.of(insn) : null;
    }

    @Nullable
    private static List<Type> findCallOperands(MixinContext context, Recipe recipe, Configuration dirty) {
        MethodQualifier call = getCallQualifier(dirty);
        if (call == null) return null;

        TargetPair dirtyTarget = recipe.getDirtyTarget();
        AbstractInsnNode insn = context.injections().resolveFirst(dirtyTarget, InjectionQuery.of(dirty.getAtData()));

        boolean isStatic;
        String owner;
        if (insn instanceof MethodInsnNode minsn) {
            isStatic = minsn.getOpcode() == Opcodes.INVOKESTATIC;
            owner = minsn.owner;
        } else {
            TargetPair method = call.isFull() ? context.dirtyMethods().resolveInherited(call) : context.dirtyMethods().resolve(call);
            if (method == null) return null;
            isStatic = MethodAnalyzer.isStatic(method.methodNode());
            owner = call.internalOwnerName() != null ? call.internalOwnerName() : method.classNode().name;
        }

        return Parameters.getCallOperandTypes(isStatic ? null : Type.getObjectType(owner), call.desc());
    }

    private HandlerSignatures() {
    }
}
