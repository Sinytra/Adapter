package org.sinytra.adapter.edit.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.WrapOpOriginalCall;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

// TODO Just add @Coerce if the types are inherited
public record ReplaceParametersTransformer(int index, Type type, boolean upgradeUsage) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ReplaceParametersTransformer(int index, Type type) {
        this(index, type, true);
    }

    @Override
    public ReplaceParametersTransformer offset(int offset) {
        return new ReplaceParametersTransformer(this.index + offset, this.type, this.upgradeUsage);
    }

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        final int paramIndex = this.index + offset;

        if (Type.getArgumentCount(methodNode.desc) <= paramIndex) {
            return PatchResult.PASS;
        }

        LocalVariableLookup lvtLookup = context.methods().getLVT(methodNode);
        LocalVariableNode localVar = lvtLookup == null ? null : lvtLookup.getByParameterOrdinalOrNull(paramIndex);
        if (localVar == null) {
            LOGGER.info(MIXINPATCH, "Cannot replace parameter {}, missing local variable info in {}.{}", paramIndex, classNode.name, methodNode.name);
            return PatchResult.PASS;
        }

        LOGGER.info(MIXINPATCH, "Replacing parameter {} with type {} in {}.{}", paramIndex, this.type, classNode.name, methodNode.name);
        parameters.set(paramIndex, this.type);

        Type originalType = Type.getType(localVar.desc);
        localVar.desc = this.type.getDescriptor();
        localVar.signature = null;

        List<AbstractInsnNode> ignoreInsns = WrapOpOriginalCall.findCallVarInsns(methodNode);
        if (this.upgradeUsage && this.type.getSort() == Type.OBJECT && originalType.getSort() == Type.OBJECT) {
            // Replace variable usages with the new type
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (ignoreInsns.contains(insn)) {
                    continue;
                }

                // FIXME Sometimes we need to change the owner in insns, sometimes we don't
                if (insn instanceof VarInsnNode varInsn && varInsn.var == localVar.index) {
                    int nextOp = insn.getNext().getOpcode();
                    if (nextOp != Opcodes.IFNULL && nextOp != Opcodes.IFNONNULL) {
                        TypeAdapter typeFix = context.getTypeAdapter(type, originalType);
                        // If this is a wrap operation, make an educated guess and try adapting the instance type
                        if (typeFix == null && context.methodAnnotation().matchesDesc(MixinAnnotations.WRAP_OPERATION)) {
                            List<Type> params = Parameters.getParameterTypes(methodNode.desc);
                            LocalVariableNode firstParam = lvtLookup.getByParameterOrdinalOrNull(0);
                            if (!params.isEmpty() && firstParam != null) {
                                typeFix = context.getTypeAdapter(params.getFirst(), originalType);
                                if (typeFix != null) {
                                    varInsn.var = firstParam.index;
                                }
                            }
                        }
                        if (typeFix != null) {
                            typeFix.apply(methodNode.instructions, varInsn);
                        }
                    }
                }
            }

            // Calls made on the variable must target the new type unless its value was adapted back to the original above
            MethodAnalyzer.forEachReceiver(methodNode, (call, receiver) -> {
                if (call.owner.equals(originalType.getInternalName())
                    && receiver.stream().anyMatch(p -> p instanceof VarInsnNode varInsn && varInsn.var == localVar.index)
                ) {
                    call.owner = this.type.getInternalName();
                }
            });
        }

        return PatchResult.COMPUTE_FRAMES;
    }
}
