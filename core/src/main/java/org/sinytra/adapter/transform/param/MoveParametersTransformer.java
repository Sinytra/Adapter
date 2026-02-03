package org.sinytra.adapter.transform.param;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.analysis.locals.LVTSnapshot;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.List;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public record MoveParametersTransformer(int from, int to) implements ParameterTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(ClassNode classNode, MethodNode methodNode, MixinContext context, List<Type> parameters, int offset) {
        final int paramIndex = this.from + offset;

        LOGGER.info(MIXINPATCH, "Moving parameter from index {} to {} in method {}.{}", this.from, this.to, classNode.name, methodNode.name);

        LocalVariableLookup lookup = new LocalVariableLookup(methodNode);
        LocalVariableNode localVar = lookup.getByParameterOrdinal(paramIndex);

        if (paramIndex < methodNode.parameters.size()) {
            ParameterNode parameter = methodNode.parameters.remove(paramIndex);
            methodNode.parameters.add(this.to > paramIndex ? this.to - 1 : this.to, parameter);
        }

        int tempIndex = -999;
        AdapterUtil.replaceLVT(methodNode, idx -> idx == localVar.index ? tempIndex : idx);

        LVTSnapshot.with(methodNode, () -> methodNode.localVariables.remove(localVar));
        parameters.remove(paramIndex);

        Type type = Type.getType(localVar.desc);
        localVar.index = lookup.getByParameterOrdinal(this.to).index + offset;

        LVTSnapshot.with(methodNode, () -> methodNode.localVariables.add(localVar.index, localVar));
        AdapterUtil.replaceLVT(methodNode, idx -> idx == tempIndex ? localVar.index : idx);

        parameters.add(this.to > paramIndex ? this.to - 1 : this.to, type);

        return PatchResult.COMPUTE_FRAMES;
    }
}
