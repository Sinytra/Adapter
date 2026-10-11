package org.sinytra.adapter.analysis.locals;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.analysis.method.MethodHelper;
import org.sinytra.adapter.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.edit.param.TransformParameters;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;

public final class LocalVarAnalyzer {
    private static final Logger LOGGER = LogUtils.getLogger();

    public record CapturedLocals(TargetPair target, boolean isStatic, int paramLocalStart, int paramLocalEnd, int lvtOffset,
                                 List<Type> expected, LocalVariableLookup lvt) {
    }

    private record CapturedParameters(
        /// The index of the first captured parameter
        int start,
        List<Type> types
    ) {
    }

    @Nullable
    private static CapturedParameters findCapturedParameters(MethodNode methodNode) {
        List<Parameter> params = Parameters.parse(methodNode);
        for (int i = 0; i < params.size() - 1; i++) {
            if (MethodParameters.ParamGroup.CI_CIR.predicate().test(params.get(i))) {
                List<Type> types = params.subList(i + 1, params.size()).stream()
                    .takeWhile(p -> !p.isLocalOrShare())
                    .map(Parameter::type)
                    .toList();
                return new CapturedParameters(i + 1, types);
            }
        }
        return null;
    }

    public static boolean capturesLocals(MixinContext context) {
        CapturedParameters captured = findCapturedParameters(context.methodNode());
        return captured != null && !captured.types().isEmpty();
    }

    @Nullable
    public static CapturedLocals findCapturedLocals(MixinContext context, Recipe recipe) {
        TargetPair target = recipe.getDirtyTarget();
        return target != null ? findCapturedLocals(context, target) : null;
    }

    @Nullable
    public static CapturedLocals findCapturedLocals(MixinContext context, TargetPair dirtyTarget) {
        MethodNode methodNode = context.methodNode();
        CapturedParameters captured = findCapturedParameters(methodNode);
        if (captured == null) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", context.methodAnnotation().getDesc());
            return null;
        }
        if (captured.types().isEmpty()) {
            return null;
        }
        LocalVariableLookup lvt = context.methods().getLVT(methodNode);
        if (lvt == null) {
            LOGGER.debug("Missing local variable table in injector {}", methodNode.name);
            return null;
        }
        boolean isStatic = MethodHelper.isStatic(methodNode);
        int end = captured.start() + captured.types().size() - 1;
        return new CapturedLocals(dirtyTarget, isStatic, captured.start(), end, isStatic ? 0 : 1, captured.types(), lvt);
    }

    public record CapturedLocalsInfo(CapturedLocals capturedLocals, LayeredParamsDiffSnapshot diff, List<Type> availableTypes) {
    }

    @Nullable
    public static CapturedLocalsInfo getCapturedLocals(MixinContext context, TargetPair dirtyTarget) {
        CapturedLocals capturedLocals = findCapturedLocals(context, dirtyTarget);
        if (capturedLocals == null) return null;

        // Get available local variables at the injection point in the target method
        List<LocalVariable> available = context.methods().getTargetMethodLocals(capturedLocals.target());
        if (available == null) return null;

        List<Type> availableTypes = available.stream().map(LocalVariable::type).toList();
        // Compare expected and available params
        LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(capturedLocals.expected(), availableTypes);
        if (diff == null) return null;

        return new CapturedLocalsInfo(capturedLocals, diff, availableTypes);
    }

    public static List<VarInsnNode> findStoreInsns(MethodNode methodNode, int index) {
        List<VarInsnNode> stores = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == index && OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
                stores.add(varInsn);
            }
        }
        return stores;
    }

    public static InsnList findInitializerInsns(MethodNode methodNode, int index) {
        return collectInitializerInsns(methodNode, index, load -> {
        });
    }

    public static Optional<LocalVariableNode> findLocalWithInitializer(MethodNode method, String desc, InsnList initializer) {
        if (method.localVariables == null) {
            return Optional.empty();
        }
        return AdapterUtil.findOne(method.localVariables, lvn -> desc.equals(lvn.desc)
            && InstructionMatcher.test(initializer, findInitializerInsns(method, lvn.index)));
    }

    private static InsnList collectInitializerInsns(MethodNode methodNode, int index, Consumer<VarInsnNode> onLoad) {
        InsnList insns = new InsnList();
        List<VarInsnNode> stores = findStoreInsns(methodNode, index);
        if (!stores.isEmpty()) {
            for (AbstractInsnNode prev : InsnTraversal.untilLabel(stores.getFirst(), InsnTraversal.BACKWARD)) {
                if (prev instanceof VarInsnNode load && OpcodeUtil.isLoadOpcode(load.getOpcode())) {
                    onLoad.accept(load);
                }
                insns.insert(prev.clone(Map.of()));
            }
        }
        return insns;
    }

    public record CapturedLocalsUsage(
        @Nullable LocalVariableLookup targetTable,
        Int2ObjectMap<LocalVariable> usedLocals,
        Int2IntMap usageCount,
        Int2ObjectMap<InsnList> varInsnLists
    ) {
    }

    public record CapturedLocalsTransform(Collection<Integer> used, TransformParameters remover, Collection<LocalVariableNode> usedLocalNodes) {
        public CapturedLocalsUsage getUsage(CapturedLocals captured, MixinContext context) {
            LocalVariableLookup targetTable = context.methods().getLVT(captured.target().methodNode());
            Int2ObjectMap<LocalVariable> usedLocals = new Int2ObjectOpenHashMap<>();
            Int2ObjectMap<InsnList> varInsnLists = new Int2ObjectOpenHashMap<>();
            Int2IntMap usageCount = new Int2IntOpenHashMap();

            // Captured locals are the locals available at the injection point, in order
            List<LocalVariable> available = context.methods().getTargetMethodLocals(captured.target());
            if (targetTable != null && available != null) {
                this.used.forEach(ordinal -> {
                    int localIndex = ordinal - captured.paramLocalStart();
                    if (localIndex >= 0 && localIndex < available.size()) {
                        LocalVariable local = available.get(localIndex);
                        usedLocals.put((int) ordinal, local);
                        findVariableInitializerInsns(captured.target().methodNode(), captured.isStatic(), local.index(), varInsnLists, usageCount);
                    }
                });
            }
            return new CapturedLocalsUsage(targetTable, usedLocals, usageCount, varInsnLists);
        }
    }

    public static CapturedLocalsTransform analyzeCapturedLocals(CapturedLocals capturedLocals, MethodNode methodNode) {
        // Find used captured locals
        int paramLocalStart = capturedLocals.paramLocalStart();
        LocalVariableLookup table = capturedLocals.lvt();
        Set<Integer> used = new HashSet<>();
        Set<LocalVariableNode> usedLocalNodes = new HashSet<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn) {
                LocalVariableNode node = table.getByIndexOrNull(varInsn.var);
                if (node == null)
                    continue;
                int ordinal = table.getParameterOrdinal(node);
                if (ordinal == -1)
                    continue;
                if (ordinal >= paramLocalStart && ordinal <= capturedLocals.paramLocalEnd()) {
                    used.add(ordinal);
                    usedLocalNodes.add(node);
                }
            }
        }
        // Remove unused captured locals
        TransformParameters remover = TransformParameters.builder()
            .chain(b -> IntStream.range(paramLocalStart, capturedLocals.paramLocalEnd() + 1)
                .filter(i -> !used.contains(i))
                .boxed().sorted(Collections.reverseOrder())
                .forEach(b::remove))
            .build();
        return new CapturedLocalsTransform(used, remover, usedLocalNodes);
    }

    public static void findVariableInitializerInsns(MethodNode methodNode, boolean isStatic, int index, Int2ObjectMap<InsnList> varInsnLists, Int2IntMap usageCount) {
        InsnList insns = collectInitializerInsns(methodNode, index, load -> {
            // TODO Handle method params
            if (isStatic || load.var != 0) {
                if (!varInsnLists.containsKey(load.var)) {
                    findVariableInitializerInsns(methodNode, isStatic, load.var, varInsnLists, usageCount);
                }
                usageCount.compute(load.var, (key, existing) -> existing == null ? 1 : existing + 1);
            }
        });
        varInsnLists.put(index, insns);
    }

    private LocalVarAnalyzer() {
    }
}
