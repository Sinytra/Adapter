package org.sinytra.adapter.pipeline.processor.signature;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.WrapOpOriginalCall;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.processor.WrapOpSurgeon;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public class OperationCallStep implements SignatureStep {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public TxResult apply(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getParameters() == null) return TxResult.FAIL;

        // Find original.call(...)
        List<List<AbstractInsnNode>> cleanCalls = WrapOpOriginalCall.findAll(context.unmodifiedMethodNode());
        List<List<AbstractInsnNode>> dirtyCalls = WrapOpOriginalCall.findAll(context.methodNode());
        if (cleanCalls.size() != dirtyCalls.size()) {
            return TxResult.FAIL;
        }

        for (int i = 0; i < dirtyCalls.size(); i++) {
            if (!upgradeOriginalCall(context, dirty, cleanCalls.get(i), dirtyCalls.get(i))) {
                return TxResult.FAIL;
            }
        }

        if (dirty.getAtData() != null) {
            // Upgrade calls on "instance" variable
            AbstractInsnNode cleanInsn = context.injections().resolve(recipe.getCleanTarget());
            AbstractInsnNode dirtyInsn = context.injections().resolve(recipe.getDirtyTarget(), InjectionQuery.of(dirty.getAtData()).keepingShift());
            if (cleanInsn instanceof MethodInsnNode cleanMinsn && dirtyInsn instanceof MethodInsnNode dirtyMinsn) {
                List<Type> methodParams = dirty.getParameters().getTypes(MethodParameters.ParamGroup.METHOD_PARAMS);
                if (!WrapOpSurgeon.tryUpgrade(context, recipe, methodParams, cleanMinsn, dirtyMinsn)) {
                    LOGGER.debug(MIXINPATCH, "Could not upgrade parameter usages of {}", context.getMixinId());
                }
            }
        }

        return TxResult.SUCCESS;
    }

    private static boolean upgradeOriginalCall(MixinContext context, Configuration dirty, List<AbstractInsnNode> cleanCallInsns, List<AbstractInsnNode> dirtyCallInsns) {
        LocalVariableLookup lookup = LocalVariableLookup.of(context.methodNode());
        if (lookup == null || !(dirtyCallInsns.getLast() instanceof MethodInsnNode dirtyInvoke)
            || WrapOpOriginalCall.parse(context.unmodifiedMethodNode(), cleanCallInsns) == null
            || WrapOpOriginalCall.parse(context.methodNode(), dirtyCallInsns) == null) {
            return false;
        }

        List<Parameter> params = dirty.getParameters().get(MethodParameters.ParamGroup.METHOD_PARAMS);
        List<WrapOpOriginalCall.CallArg> args = new ArrayList<>();
        for (int i = 0; i < params.size(); i++) {
            LocalVariableNode local = lookup.getByParameterOrdinalOrNull(i);
            if (local == null) {
                return false;
            }
            Type type = params.get(i).type();
            List<AbstractInsnNode> loadInsns = new ArrayList<>(List.of(AdapterUtil.loadType(type, local.index)));
            if (type.getSort() < Type.ARRAY) {
                loadInsns.add(AdapterUtil.box(type));
            }
            args.add(WrapOpOriginalCall.CallArg.create(i, loadInsns));
        }
        WrapOpOriginalCall reconstruct = new WrapOpOriginalCall(args);

        AbstractInsnNode arrayInit = dirtyCallInsns.get(1);
        AbstractInsnNode start = arrayInit.getNext(); // After ANEWARRAY
        AbstractInsnNode end = dirtyInvoke.getPrevious(); // Before INVOKEVIRTUAL call()

        InsnList methodInsns = context.methodNode().instructions;
        AdapterUtil.replaceRangeInclusive(methodInsns, start, end, reconstruct.merge());

        // Change array size
        if (AdapterUtil.getIntConstValue(arrayInit.getPrevious()).isPresent()) {
            methodInsns.set(arrayInit.getPrevious(), AdapterUtil.getIntConstInsn(args.size()));
        } else {
            InsnList loadNewSize = AdapterUtil.insnsWithAdapter(c -> {
                c.pop();
                c.iconst(args.size());
            });
            methodInsns.insertBefore(arrayInit, loadNewSize);
        }

        return true;
    }
}
