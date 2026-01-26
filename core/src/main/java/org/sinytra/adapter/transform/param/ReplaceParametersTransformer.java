package org.sinytra.adapter.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.types.BytecodeFixerUpper;
import org.sinytra.adapter.types.TypeAdapter;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.transform.param.ParamTransformationUtil.findWrapOperationOriginalCall;
import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

// TODO Just add @Coerce if the types are inherited
public record ReplaceParametersTransformer(int index, Type type, boolean upgradeUsage) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ReplaceParametersTransformer(int index, Type type) {
        this(index, type, true);
    }

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        final int paramIndex = this.index + offset;

        if (methodNode.parameters.size() <= paramIndex) {
            return PatchResult.PASS;
        }

        LOGGER.info(MIXINPATCH, "Replacing parameter {} with type {} in {}.{}", paramIndex, this.type, classNode.name, methodNode.name);
        parameters.set(paramIndex, this.type);

        LocalVariableLookup lvtLookup = new LocalVariableLookup(methodNode);
        LocalVariableNode localVar = lvtLookup.getByParameterOrdinal(paramIndex);
        Type originalType = Type.getType(localVar.desc);
        localVar.desc = this.type.getDescriptor();
        localVar.signature = null;

        List<AbstractInsnNode> ignoreInsns = findWrapOperationOriginalCall(methodNode, context);
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        if (this.upgradeUsage && this.type.getSort() == Type.OBJECT && originalType.getSort() == Type.OBJECT) {
            // Replace variable usages with the new type
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (ignoreInsns.contains(insn)) {
                    continue;
                }

                // FIXME Sometimes we need to change the owner in insns, sometimes we don't
                if (insn instanceof VarInsnNode varInsn && varInsn.var == localVar.index) {
                    int nextOp = insn.getNext().getOpcode();
                    if (bfu != null && nextOp != Opcodes.IFNULL && nextOp != Opcodes.IFNONNULL) {
                        TypeAdapter typeFix = bfu.getTypeAdapter(type, originalType);
                        // If this is a wrap operation, make an educated guess and try adapting the instance type
                        if (typeFix == null && context.methodAnnotation().matchesDesc(MixinAnnotations.WRAP_OPERATION)) {
                            List<Type> params = Parameters.getParameterTypes(methodNode.desc);
                            if (!params.isEmpty()) {
                                typeFix = bfu.getTypeAdapter(params.getFirst(), originalType);
                                if (typeFix != null) {
                                    varInsn.var = lvtLookup.getByParameterOrdinal(0).index;
                                }
                            }
                        }
                        if (typeFix != null) {
                            typeFix.apply(methodNode.instructions, varInsn);
                        }
                    }
                }

                if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(originalType.getInternalName())) {
                    List<AbstractInsnNode> insns = MethodCallAnalyzer.getMethodCallInsns(methodNode, minsn);
                    // Find var load instruction
                    for (AbstractInsnNode callInsn : insns) {
                        if (callInsn instanceof VarInsnNode varinsn && varinsn.var == localVar.index) {
                            minsn.owner = this.type.getInternalName();
                        }
                    }
                }
            }
        }

        return PatchResult.COMPUTE_FRAMES;
    }
}
