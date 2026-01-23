package org.sinytra.adapter.next.pipeline.processor.wrapop;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.next.env.param.Parameter;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.processor.Processor;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class WrapOpParamsProcessor implements Processor {
    private static final MethodQualifier WO_ORIGINAL_CALL = new MethodQualifier("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (dirty.getParameters() == null) return TxResult.FAIL;

        // Find original.call(...)
        List<List<AbstractInsnNode>> cleanCalls = MethodCallAnalyzer.getAllMethodCallSrcInsnsInclusive(context.unmodifiedMethodNode(), WO_ORIGINAL_CALL);
        List<List<AbstractInsnNode>> dirtyCalls = MethodCallAnalyzer.getAllMethodCallSrcInsnsInclusive(context.methodNode(), WO_ORIGINAL_CALL);
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

        // Upgrade calls on "instance" variable
        AbstractInsnNode cleanInsn = context.methods().findInjectionTargetInsn(recipe.getCleanTarget());
        AbstractInsnNode dirtyInsn = context.methods().findInjectionTargetInsn(recipe.getDirtyTarget());
        if (cleanInsn instanceof MethodInsnNode cleanMinsn && dirtyInsn instanceof MethodInsnNode dirtyMinsn) {
            List<Type> methodParams = dirty.getParameters().getTypes(MethodParameters.ParamGroup.METHOD_PARAMS);
            boolean result = WrapOpSurgeon.tryUpgrade(context, recipe, methodParams, cleanMinsn, dirtyMinsn);
            if (!result) {
                return TxResult.FAIL;
            }
        }

        return TxResult.PASS;
    }

    @Nullable
    private static WrapOpOriginalCall parseOriginalCall(MethodNode methodNode, List<AbstractInsnNode> callInsns) {
        if (callInsns.size() < 2) {
            return null;
        }
        AbstractInsnNode singleArg = callInsns.get(1);
        if (!(singleArg instanceof TypeInsnNode tinsn) || tinsn.getOpcode() != Opcodes.ANEWARRAY) {
            return null;
        }
        List<List<AbstractInsnNode>> arrayInitArgs = WrapOpAnalyzer.groupArrayInitializers(methodNode, singleArg);
        return WrapOpOriginalCall.parse(arrayInitArgs);
    }

    private static boolean upgradeOriginalCall(MixinContext context, Configuration dirty, List<AbstractInsnNode> cleanCallInsns, List<AbstractInsnNode> dirtyCallInsns) {
        WrapOpOriginalCall cleanCall = parseOriginalCall(context.unmodifiedMethodNode(), cleanCallInsns);
        if (cleanCall == null) return false;

        WrapOpOriginalCall dirtyCall = parseOriginalCall(context.methodNode(), dirtyCallInsns);
        if (dirtyCall == null) return false;

        AbstractInsnNode arrayInit = dirtyCallInsns.get(1);
        List<Parameter> params = dirty.getParameters().get(MethodParameters.ParamGroup.METHOD_PARAMS);

        LocalVariableLookup lookup = new LocalVariableLookup(context.methodNode());
        List<WrapOpOriginalCall.CallArg> args = IntStream.range(0, params.size())
            .mapToObj(i -> {
                Parameter param = params.get(i);
                int index = lookup.getByParameterOrdinal(i).index;
                return WrapOpOriginalCall.CallArg.create(i, List.of(AdapterUtil.loadType(param.getType(), index)));
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

    public record WrapOpOriginalCall(List<CallArg> args) {
        public record CallArg(List<AbstractInsnNode> pre, List<AbstractInsnNode> load, List<AbstractInsnNode> post) {
            public static CallArg create(int index, List<AbstractInsnNode> load) {
                List<AbstractInsnNode> pre = List.of(
                    new InsnNode(Opcodes.DUP),
                    AdapterUtil.getIntConstInsn(index)
                );
                List<AbstractInsnNode> post = List.of(
                    new InsnNode(Opcodes.AASTORE)
                );
                return new WrapOpOriginalCall.CallArg(pre, load, post);
            }

            @Nullable
            public static CallArg parse(List<AbstractInsnNode> insns) {
                if (insns.size() < 4 || insns.getFirst().getOpcode() != Opcodes.DUP)
                    return null;
                AbstractInsnNode idxInsn = insns.get(1);
                if (AdapterUtil.getIntConstValue(idxInsn).isEmpty())
                    return null;

                AbstractInsnNode aastore = insns.getLast();
                if (aastore.getOpcode() != Opcodes.AASTORE)
                    return null;

                return new CallArg(
                    insns.subList(0, 2),
                    insns.subList(2, insns.size() - 1),
                    List.of(insns.getLast())
                );
            }
        }

        public List<AbstractInsnNode> merge() {
            return this.args.stream()
                .flatMap(a -> Stream.of(a.pre, a.load, a.post).flatMap(Collection::stream))
                .toList();
        }

        @Nullable
        public static WrapOpOriginalCall parse(List<List<AbstractInsnNode>> argsInsns) {
            if (argsInsns.isEmpty())
                return null;

            List<CallArg> args = new ArrayList<>();
            for (List<AbstractInsnNode> insns : argsInsns) {
                CallArg arg = CallArg.parse(insns);
                if (arg == null) {
                    return null;
                }
                args.add(arg);
            }

            return new WrapOpOriginalCall(args);
        }
    }
}
