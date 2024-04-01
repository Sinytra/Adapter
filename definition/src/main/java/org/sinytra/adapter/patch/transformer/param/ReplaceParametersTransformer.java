package org.sinytra.adapter.patch.transformer.param;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.LocalVariableLookup;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;
import static org.sinytra.adapter.patch.transformer.param.ParamTransformationUtil.findWrapOperationOriginalCall;

public record ReplaceParametersTransformer(int index, Type type) implements ParameterTransformer {
    static final Codec<ReplaceParametersTransformer> CODEC = RecordCodecBuilder.create(in -> in.group(
        Codec.intRange(0, 255).fieldOf("index").forGetter(ReplaceParametersTransformer::index),
        AdapterUtil.TYPE_CODEC.fieldOf("type").forGetter(ReplaceParametersTransformer::type)
    ).apply(in, ReplaceParametersTransformer::new));

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context, List<Type> parameters, int offset) {
        final int paramIndex = this.index + offset;

        if (methodNode.parameters.size() <= paramIndex) {
            return Patch.Result.PASS;
        }

        LOGGER.info(MIXINPATCH, "Replacing parameter {} with type {} in {}.{}", this.index, this.type, classNode.name, methodNode.name);
        parameters.set(this.index, this.type);

        LocalVariableLookup lvtLookup = new LocalVariableLookup(methodNode);
        LocalVariableNode localVar = lvtLookup.getByParameterOrdinal(paramIndex);
        Type originalType = Type.getType(localVar.desc);
        localVar.desc = this.type.getDescriptor();
        localVar.signature = null;
        
        List<AbstractInsnNode> ignoreInsns = findWrapOperationOriginalCall(methodNode, methodContext);
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        if (this.type.getSort() == Type.OBJECT && originalType.getSort() == Type.OBJECT) {
            // Replace variable usages with the new type
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (ignoreInsns.contains(insn)) {
                    continue;
                }
                if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(originalType.getInternalName())) {
                    // Find var load instruction
                    AbstractInsnNode previous = minsn.getPrevious();
                    if (previous != null) {
                        do {
                            // Limit scope to the current label / line only
                            if (previous instanceof LabelNode || previous instanceof LineNumberNode) {
                                break;
                            }
                            if (previous instanceof VarInsnNode varinsn && varinsn.var == localVar.index) {
                                minsn.owner = type.getInternalName();
                                break;
                            }
                        } while ((previous = previous.getPrevious()) != null);
                    }
                }
                if (insn instanceof VarInsnNode varInsn && varInsn.var == localVar.index) {
                    int nextOp = insn.getNext().getOpcode();
                    if (bfu != null && nextOp != Opcodes.IFNULL && nextOp != Opcodes.IFNONNULL) {
                        TypeAdapter typeFix = bfu.getTypeAdapter(type, originalType);
                        if (typeFix != null) {
                            typeFix.apply(methodNode.instructions, varInsn);
                        }
                    }
//                    if (this.lvtFixer != null) {
//                        this.lvtFixer.accept(varInsn.var, varInsn, methodNode.instructions);
//                    }
                }
            }
        }
        
        return Patch.Result.COMPUTE_FRAMES;
    }
}
