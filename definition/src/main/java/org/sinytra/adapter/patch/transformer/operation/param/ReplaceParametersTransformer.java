package org.sinytra.adapter.patch.transformer.operation.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.param.Parameters;
import org.sinytra.adapter.patch.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;
import static org.sinytra.adapter.patch.transformer.operation.param.ParamTransformationUtil.findWrapOperationOriginalCall;

// TODO Just add @Coerce if the types are inherited
public record ReplaceParametersTransformer(int index, Type type, boolean upgradeUsage) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public ReplaceParametersTransformer(int index, Type type) {
        this(index, type, true);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int paramIndex = this.index + offset;

        if (methodNode.parameters.size() <= paramIndex) {
            return Patch.Result.PASS;
        }

        LOGGER.info(MIXINPATCH, "Replacing parameter {} with type {} in {}.{}", paramIndex, this.type, classNode.name, methodNode.name);
        parameters.set(paramIndex, this.type);

        LocalVariableLookup lvtLookup = new LocalVariableLookup(methodNode);
        LocalVariableNode localVar = lvtLookup.getByParameterOrdinal(paramIndex);
        Type originalType = Type.getType(localVar.desc);
        localVar.desc = this.type.getDescriptor();
        localVar.signature = null;

        List<AbstractInsnNode> ignoreInsns = findWrapOperationOriginalCall(methodNode, methodContext);
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
                        if (typeFix == null && methodContext.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
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

        return Patch.Result.COMPUTE_FRAMES;
    }
}
