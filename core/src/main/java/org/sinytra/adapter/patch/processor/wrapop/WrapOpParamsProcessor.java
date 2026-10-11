package org.sinytra.adapter.patch.processor.wrapop;

import com.mojang.logging.LogUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.env.param.Parameter;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public class WrapOpParamsProcessor implements Processor {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getParameters() == null) return TxResult.FAIL;

        // Find original.call(...)
        List<List<AbstractInsnNode>> cleanCalls = WrapOpOriginalCall.findAll(context.unmodifiedMethodNode());
        List<List<AbstractInsnNode>> dirtyCalls = WrapOpOriginalCall.findAll(context.methodNode());
        if (cleanCalls.size() != dirtyCalls.size()) {
            return TxResult.FAIL;
        }

        for (int i = 0; i < dirtyCalls.size(); i++) {
            List<AbstractInsnNode> cleanCall = cleanCalls.get(i);
            List<AbstractInsnNode> dirtyCall = dirtyCalls.get(i);
            if (!upgradeOriginalCall(context, dirty, cleanCall, dirtyCall)) {
                return TxResult.FAIL;
            }
        }

        if (dirty.getAtData() != null) {
            // Upgrade calls on "instance" variable
            AbstractInsnNode cleanInsn = context.methods().findInjectionTargetInsn(recipe.getCleanTarget());
            AbstractInsnNode dirtyInsn = context.methods().findInjectionTargetInsn(recipe.getDirtyTarget(), dirty.getAtData());
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
        WrapOpOriginalCall cleanCall = WrapOpOriginalCall.parse(context.unmodifiedMethodNode(), cleanCallInsns);
        if (cleanCall == null) return false;

        WrapOpOriginalCall dirtyCall = WrapOpOriginalCall.parse(context.methodNode(), dirtyCallInsns);
        if (dirtyCall == null) return false;

        AbstractInsnNode arrayInit = dirtyCallInsns.get(1);
        List<Parameter> params = dirty.getParameters().get(MethodParameters.ParamGroup.METHOD_PARAMS);

        LocalVariableLookup lookup = context.methods().getLVT(context.methodNode());
        if (lookup == null || IntStream.range(0, params.size()).anyMatch(i -> lookup.getByParameterOrdinalOrNull(i) == null)) {
            return false;
        }

        List<WrapOpOriginalCall.CallArg> args = IntStream.range(0, params.size())
            .mapToObj(i -> {
                Parameter param = params.get(i);
                int index = lookup.getByParameterOrdinalOrNull(i).index;
                Type type = param.type();

                List<AbstractInsnNode> loadInsns = new ArrayList<>();
                loadInsns.add(AdapterUtil.loadType(type, index));
                if (type.getSort() < Type.ARRAY) {
                    loadInsns.add(AdapterUtil.box(type));
                }

                return WrapOpOriginalCall.CallArg.create(i, loadInsns);
            })
            .toList();
        WrapOpOriginalCall reconstruct = new WrapOpOriginalCall(args);

        AbstractInsnNode start = arrayInit.getNext(); // After ANEWARRAY
        AbstractInsnNode dirtyInvoke = dirtyCallInsns.getLast();
        if (!(dirtyInvoke instanceof MethodInsnNode)) return false;
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
