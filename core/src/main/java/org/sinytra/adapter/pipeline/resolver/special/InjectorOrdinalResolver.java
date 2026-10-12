package org.sinytra.adapter.pipeline.resolver.special;

import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.InsnComparator;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.locals.LocalsAnalyzer;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.mixin.MixinBridge.LocalCandidate;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.pipeline.resolver.Resolver;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.GeneratedVariables;
import org.sinytra.adapter.util.OpcodeUtil;
import org.sinytra.adapter.util.SingleValueHandle;

import java.util.*;
import java.util.function.Function;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_RETURN;

public class InjectorOrdinalResolver implements Resolver {
    private static final Map<String, OffsetUpdateHandler> OFFSET_HANDLERS = Map.of(
        AT_VAL_INVOKE, InvokeOffsetHandler.INSTANCE,
        AT_VAL_RETURN, ReturnOffsetHandler.INSTANCE
    );

    @Override
    public ResolutionResult resolve(MixinContext context, Recipe recipe) {
        Type returnType = recipe.clean().getReturnType();
        if (returnType == null) return ResolutionResult.pass();

        List<HandlerInstance<?, ?>> offsetHandlers = getOffsetHandlers(context, recipe, returnType);
        if (offsetHandlers.isEmpty()) return ResolutionResult.pass();

        if (recipe.targets() == null) return ResolutionResult.pass();

        boolean applied = false;
        MutableConfiguration merged = MutableConfiguration.create();
        for (HandlerInstance<?, ?> instance : offsetHandlers) {
            Configuration config = instance.apply(context, recipe);
            if (config != null) {
                applied = true;
                merged.mergeFrom(config);
            }
        }

        return applied ? ResolutionResult.success(merged) : ResolutionResult.pass();
    }

    private static List<HandlerInstance<?, ?>> getOffsetHandlers(MixinContext context, Recipe recipe, Type returnType) {
        Configuration cleanConfig = recipe.clean();
        AtData at = cleanConfig.getAtData();
        List<HandlerInstance<?, ?>> handlers = new ArrayList<>();

        at.getProperty(AtData.Keys.ORDINAL).ifPresent(ordinal -> {
            String target = at.getTarget().orElse(null);
            OffsetUpdateHandler handler = OFFSET_HANDLERS.get(at.getValue());
            if (handler != null && (!handler.requiresTarget() || target != null)) {
                OffsetUpdateHandler.Context handleContext = new OffsetUpdateHandler.Context(target, ordinal);
                HandlerInstance<?, ?> instance = new HandlerInstance<>(handler, handleContext, i ->
                    MutableConfiguration.create()
                        .setAtData(at.withOrdinal(i))
                );
                handlers.add(instance);
            }
        });

        if (context.hasFlag(MixinFlag.TARGETS_VARIABLE)) {
            LocalVariableLookup cleanTable = recipe.cleanLocalsTable();
            TargetPair cleanTarget = recipe.getCleanTarget();
            AbstractInsnNode cleanInsn = cleanTarget != null ? AdapterUtil.getFirstOrNull(context.injections().find(cleanTarget)) : null;
            if (cleanTable != null && cleanInsn != null) {
                // Handle modified ordinals
                cleanConfig.getProperty(MixinKeys.ORDINAL)
                    .flatMap(ordinal -> Optional.ofNullable(cleanTable.getInScopeOrNull(context.locals().resolveLocalIndex(cleanTarget, cleanInsn, returnType, ordinal), cleanInsn))
                        .map(lvn -> new LocalVar(lvn, ordinal, true))
                        .map(local -> new HandlerInstance<>(ModifyVariableOffsetHandler.INSTANCE, local, pair ->
                            MutableConfiguration.create()
                                .setProperty(MixinKeys.ORDINAL, pair.getFirst().ordinal())
                                .mergeFrom(pair.getSecond())
                        )))
                    // Handle modified indexes
                    .or(() -> cleanConfig.getProperty(MixinKeys.INDEX)
                        .flatMap(i -> Optional.ofNullable(cleanTable.getInScopeOrNull(i, cleanInsn))
                            .flatMap(lvn -> {
                                int ordinal = context.locals().resolveLocalOrdinal(cleanTarget, cleanInsn, Type.getType(lvn.desc), i);
                                return ordinal == -1 ? Optional.empty() : Optional.of(new LocalVar(lvn, ordinal, false));
                            })
                            .map(local -> new HandlerInstance<>(ModifyVariableOffsetHandler.INSTANCE, local, pair ->
                                MutableConfiguration.create()
                                    .setProperty(MixinKeys.INDEX, pair.getFirst().lvn().index)
                                    .mergeFrom(pair.getSecond())
                            ))
                        ))
                    .ifPresent(handlers::add);
            }
        }

        return handlers;
    }

    private interface UpdateHandler<T, U> {
        Optional<U> apply(MixinContext mixinContext, Recipe recipe, T context);
    }

    private interface OffsetUpdateHandler extends UpdateHandler<OffsetUpdateHandler.Context, Integer> {
        record Context(@Nullable String target, int ordinal) {
        }

        default boolean requiresTarget() {
            return false;
        }
    }

    private record LocalVar(LocalVariableNode lvn, int ordinal, boolean relative) {
        public LocalVar(LocalVariableNode lvn, int ordinal) {
            this(lvn, ordinal, false);
        }
    }

    private record HandlerInstance<T, U>(UpdateHandler<T, U> handler, T context, Function<U, @Nullable Configuration> applicator) {
        @Nullable
        public Configuration apply(MixinContext mixinContext, Recipe recipe) {
            Optional<U> updatedValue = this.handler.apply(mixinContext, recipe, this.context);
            if (updatedValue.isPresent()) {
                U value = updatedValue.get();
                mixinContext.recordCtxAudit("Update injection point ordinal from %s to %s", this.context, value);
                return this.applicator.apply(value);
            }
            return null;
        }
    }

    private static class InvokeOffsetHandler implements OffsetUpdateHandler {
        public static final InvokeOffsetHandler INSTANCE = new InvokeOffsetHandler();

        @Override
        public boolean requiresTarget() {
            return true;
        }

        @Override
        public Optional<Integer> apply(MixinContext mixinContext, Recipe recipe, Context context) {
            TargetPair cleanTarget = recipe.getCleanTarget();
            TargetPair dirtyTarget = recipe.getDirtyTarget();
            String target = context.target();
            int ordinal = context.ordinal();

            Multimap<String, MethodInsnNode> cleanCallsMap = MethodAnalyzer.getMethodCalls(cleanTarget.methodNode());
            Multimap<String, MethodInsnNode> dirtyCallsMap = MethodAnalyzer.getMethodCalls(dirtyTarget.methodNode());

            String remappedTarget = mixinContext.patchContext().remap(target);
            Collection<? extends AbstractInsnNode> cleanCalls = cleanCallsMap.get(remappedTarget);
            Collection<? extends AbstractInsnNode> dirtyCalls = dirtyCallsMap.get(remappedTarget);

            if (cleanCalls.size() != dirtyCalls.size()) {
                List<InstructionMatcher> cleanMatchers = cleanCalls.stream().map(MethodInsnMatcher::findSurroundingInstructions).toList();
                List<InstructionMatcher> dirtyMatchers = dirtyCalls.stream().map(MethodInsnMatcher::findSurroundingInstructions).toList();

                if (ordinal >= 0 && ordinal < cleanMatchers.size()) {
                    InstructionMatcher original = cleanMatchers.get(ordinal);
                    return AdapterUtil.findOne(dirtyMatchers, original::test).map(dirtyMatchers::indexOf);
                }
            }

            return Optional.empty();
        }
    }

    private static class ReturnOffsetHandler implements OffsetUpdateHandler {
        public static final OffsetUpdateHandler INSTANCE = new ReturnOffsetHandler();

        @Override
        public Optional<Integer> apply(MixinContext mixinContext, Recipe recipe, Context context) {
            TargetPair cleanTarget = recipe.getCleanTarget();
            TargetPair dirtyTarget = recipe.getDirtyTarget();
            int ordinal = context.ordinal();

            List<AbstractInsnNode> cleanReturnInsns = MethodAnalyzer.findReturnInsns(cleanTarget.methodNode());
            List<AbstractInsnNode> dirtyReturnInsns = MethodAnalyzer.findReturnInsns(dirtyTarget.methodNode());

            if (cleanReturnInsns.size() == dirtyReturnInsns.size()) {
                return Optional.empty();
            }

            if (ordinal < cleanReturnInsns.size()) {
                AbstractInsnNode cleanInsn = cleanReturnInsns.get(ordinal);
                InstructionMatcher original = new InstructionMatcher(cleanInsn, findReturnPrecedingInsns(cleanInsn), List.of());
                List<InstructionMatcher> dirtyMatchers = dirtyReturnInsns.stream()
                    .map(i -> new InstructionMatcher(i, findReturnPrecedingInsns(i), List.of()))
                    .toList();
                Optional<InstructionMatcher> match = AdapterUtil.findOne(dirtyMatchers, m -> original.test(m, InsnComparator.IGNORE_VAR_INDEX));
                if (match.isPresent()) {
                    return match.map(dirtyMatchers::indexOf);
                }
            }

            return findByMethodCalls(ordinal, cleanTarget, dirtyTarget);
        }
        
        private static Optional<Integer> findByMethodCalls(int ordinal, TargetPair cleanTarget, TargetPair dirtyTarget) {
            List<Pair<InsnNode, List<String>>> cleanCalls = getBlockCalls(cleanTarget.methodNode());
            List<Pair<InsnNode, List<String>>> dirtyCalls = getBlockCalls(dirtyTarget.methodNode());
            
            if (ordinal >= cleanCalls.size()) {
                return Optional.empty();
            }

            List<String> cleanCall = cleanCalls.get(ordinal).getSecond();
            for (int i = 0; i < dirtyCalls.size(); i++) {
                Pair<InsnNode, List<String>> dirtyCall = dirtyCalls.get(i);
                boolean haveCommon = !Collections.disjoint(cleanCall, dirtyCall.getSecond());
                if (haveCommon) {
                    return Optional.of(i);   
                }
            }

            return Optional.empty();
        }

        private static List<Pair<InsnNode, List<String>>> getBlockCalls(MethodNode methodNode) {
            List<Pair<InsnNode, List<String>>> blockCalls = new ArrayList<>();

            List<String> minsns = new ArrayList<>();
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof MethodInsnNode minsn) {
                    minsns.add(MethodQualifier.create(minsn).asDescriptor());
                }
                if (OpcodeUtil.isReturnOpcode(insn.getOpcode())) {
                    blockCalls.add(Pair.of((InsnNode) insn, minsns));
                    minsns = new ArrayList<>();
                }
            }
            
            // Remove non-unique calls
            List<List<String>> values = blockCalls.stream()
                .map(Pair::getSecond)
                .toList();
            Map<String, Integer> counts = new HashMap<>();
            for (List<String> list : values) {
                for (String s : list) {
                    counts.merge(s, 1, Integer::sum);
                }
            }
            for (List<String> list : values) {
                list.removeIf(s -> counts.getOrDefault(s, 0) > 1);
            }

            return blockCalls;
        }

        private static List<AbstractInsnNode> findReturnPrecedingInsns(AbstractInsnNode insn) {
            return InsnTraversal.walk(insn.getPrevious(), InsnTraversal.BACKWARD)
                .takeWhile(prev -> !OpcodeUtil.isReturnOpcode(prev.getOpcode()))
                .filter(prev -> !(prev instanceof LabelNode) && !InsnTraversal.isDebugNode(prev))
                .limit(6)
                .toList()
                .reversed();
        }
    }

    private static class ModifyVariableOffsetHandler implements UpdateHandler<LocalVar, Pair<LocalVar, @Nullable Configuration>> {
        private static final ModifyVariableOffsetHandler INSTANCE = new ModifyVariableOffsetHandler();

        @Override
        public Optional<Pair<LocalVar, @Nullable Configuration>> apply(MixinContext mixinContext, Recipe recipe, LocalVar local) {
            MethodNode methodNode = mixinContext.methodNode();

            Type[] args = Type.getArgumentTypes(methodNode.desc);
            if (args.length < 1) {
                return Optional.empty();
            }

            Type targetType = args[0];
            // Gradually expand supported types over time as necessary
            if (!targetType.equals(Type.BOOLEAN_TYPE) && !targetType.equals(Type.INT_TYPE) && !targetType.equals(Type.FLOAT_TYPE)) {
                return Optional.empty();
            }

            if (mixinContext.methodAnnotation().getValue("slice").isPresent() && local.relative()) {
                return Optional.empty();
            }

            TargetPair cleanTarget = recipe.getCleanTarget();
            TargetPair dirtyTarget = recipe.getDirtyTarget();
            if (cleanTarget == null || dirtyTarget == null) {
                return Optional.empty();
            }
            AbstractInsnNode cleanInsn = AdapterUtil.getFirstOrNull(mixinContext.injections().find(cleanTarget));
            AbstractInsnNode dirtyInsn = AdapterUtil.getFirstOrNull(mixinContext.injections().find(dirtyTarget));
            if (cleanInsn == null || dirtyInsn == null) {
                return Optional.empty();
            }
            InjectionPoints points = new InjectionPoints(cleanTarget, cleanInsn, dirtyTarget, dirtyInsn);

            return tryFindUpdatedIndex(mixinContext, targetType, recipe, local, points)
                .<Pair<LocalVar, Configuration>>map(var -> Pair.of(var, null))
                .or(() -> tryFindSyntheticVariableIndex(mixinContext, methodNode, recipe, local, points));
        }

        private record InjectionPoints(TargetPair cleanTarget, AbstractInsnNode cleanInsn, TargetPair dirtyTarget, AbstractInsnNode dirtyInsn) {
        }

        private static int indexOfOrdinal(List<LocalCandidate> candidates, int ordinal) {
            return candidates.stream().filter(c -> c.ordinal() == ordinal).mapToInt(LocalCandidate::index).findFirst().orElse(-1);
        }

        /**
         * Handle situations where a mixin is attempting to modify a variable that is used immediately after its modified.
         * However, due to the nature of binary patches, a new variable might have been introduced earlier in the method, which is being used in its place now.
         * In these cases, we'll find the new variable and update the mixin's index
         * <p>
         * As an example, let's have a look at LivingEntity#actuallyHurt
         * <pre>{@code
         * == Original code ==
         *    INVOKEVIRTUAL net/minecraft/world/entity/player/Player.getHealth ()F
         *    FLOAD 2
         *    FSUB
         *    INVOKEVIRTUAL net/minecraft/world/entity/player/Player.setHealth (F)V
         * == Patched code ==
         * >> INVOKESPECIAL <modifyvar>
         * >> FSTORE 2
         *    INVOKEVIRTUAL net/minecraft/world/entity/player/Player.getHealth ()F
         * != FLOAD 3
         *    FSUB
         *    INVOKEVIRTUAL net/minecraft/world/entity/player/Player.setHealth (F)V
         * == Resulting code ==
         * >> INVOKESPECIAL <modifyvar>
         * >> FSTORE 3
         *    INVOKEVIRTUAL net/minecraft/world/entity/player/Player.getHealth ()F
         *    FLOAD 3
         *    FSUB
         *    INVOKEVIRTUAL net/minecraft/world/entity/player/Player.setHealth (F)V
         * }</pre>
         */
        private static Optional<Pair<LocalVar, @Nullable Configuration>> tryFindSyntheticVariableIndex(MixinContext context, MethodNode methodNode, Recipe recipe, LocalVar local, InjectionPoints points) {
            int ordinal = local.ordinal();
            Type variableType = Type.getReturnType(methodNode.desc);
            LocalVariableLookup cleanTable = recipe.cleanLocalsTable();
            LocalVariableLookup dirtyTable = recipe.dirtyLocalsTable();
            if (cleanTable == null || dirtyTable == null) {
                return Optional.empty();
            }
            List<LocalCandidate> cleanLocals = context.locals().findSelectableLocals(points.cleanTarget(), points.cleanInsn(), variableType);
            List<LocalCandidate> dirtyLocals = context.locals().findSelectableLocals(points.dirtyTarget(), points.dirtyInsn(), variableType);
            if (cleanLocals.size() == dirtyLocals.size()) {
                int variableIndex = indexOfOrdinal(dirtyLocals, ordinal);
                if (variableIndex != -1) {
                    AbstractInsnNode cleanInsn = recipe.getCleanInjectionInsn();
                    AbstractInsnNode dirtyInsn = context.injections().resolve(recipe.getDirtyTarget());
                    if (cleanInsn != null && dirtyInsn != null) {
                        for (AbstractInsnNode insn : InsnTraversal.untilLabel(cleanInsn, InsnTraversal.FORWARD, true)) {
                            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                            if (handle != null && handle.get() == variableIndex) {
                                // We found out the variable is used right after our injection point
                                // Now let's check if it its index remain the same in the dirty target
                                List<SingleValueHandle<Integer>> dirtyVars = getUsedVariablesInLabel(dirtyInsn, insn.getOpcode());
                                if (dirtyVars.size() == 1) {
                                    int dirtyIndex = dirtyVars.getFirst().get();
                                    if (dirtyIndex != variableIndex) {
                                        // Find new ordinal by index
                                        LocalVariableNode lvn = dirtyTable.getByIndexOrNull(dirtyIndex);
                                        if (lvn == null) {
                                            return Optional.empty();
                                        }
                                        int dirtyOrdinal = context.locals().resolveLocalOrdinal(points.dirtyTarget(), points.dirtyInsn(), Type.getType(lvn.desc), dirtyIndex);
                                        if (dirtyOrdinal == -1) {
                                            return Optional.empty();
                                        }
                                        return Optional.of(Pair.of(
                                            new LocalVar(lvn, dirtyOrdinal),
                                            MutableConfiguration.create()
                                                .setProperty(MixinKeys.ARGS_ONLY, false)
                                        ));
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
            return Optional.empty();
        }

        private static List<SingleValueHandle<Integer>> getUsedVariablesInLabel(AbstractInsnNode start, int opcode) {
            List<SingleValueHandle<Integer>> list = new ArrayList<>();
            for (AbstractInsnNode insn : InsnTraversal.untilLabel(start, InsnTraversal.FORWARD, true)) {
                if (insn.getOpcode() == opcode) {
                    SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                    if (handle != null) {
                        list.add(handle);
                    }
                }
            }
            return list;
        }

        private static Optional<LocalVar> tryFindUpdatedIndex(MixinContext context, Type targetType, Recipe recipe, LocalVar local, InjectionPoints points) {
            int ordinal = local.ordinal();
            LocalVariableLookup cleanVarLookup = recipe.cleanLocalsTable();
            LocalVariableLookup dirtyVarLookup = recipe.dirtyLocalsTable();
            if (cleanVarLookup == null || dirtyVarLookup == null) {
                return Optional.empty();
            }
            List<LocalCandidate> cleanLocals = context.locals().findSelectableLocals(points.cleanTarget(), points.cleanInsn(), targetType);
            LocalVariableNode cleanLocal = cleanVarLookup.getInScopeOrNull(indexOfOrdinal(cleanLocals, ordinal), points.cleanInsn());
            if (cleanLocal == null) {
                return Optional.empty();
            }
            if (!GeneratedVariables.isGeneratedVariableName(cleanLocal.name, Type.getType(cleanLocal.desc))) {
                return Optional.empty();
            }

            List<LocalCandidate> dirtyLocals = context.locals().findSelectableLocals(points.dirtyTarget(), points.dirtyInsn(), targetType);
            int dirtyIndex = indexOfOrdinal(dirtyLocals, ordinal);
            if (cleanLocals.size() != dirtyLocals.size() || dirtyIndex == -1) {
                return findReplacementLocal(points.cleanTarget().methodNode(), points.dirtyTarget().methodNode(), cleanLocal)
                    .flatMap(var -> {
                        int dirtyOrdinal = context.locals().resolveLocalOrdinal(points.dirtyTarget(), points.dirtyInsn(), Type.getType(var.desc), var.index);
                        return dirtyOrdinal == -1 ? Optional.empty() : Optional.of(new LocalVar(var, dirtyOrdinal));
                    });
            }
            LocalVariableNode dirtyLocal = dirtyVarLookup.getInScopeOrNull(dirtyIndex, points.dirtyInsn());
            if (dirtyLocal == null) {
                return Optional.empty();
            }
            if (!local.relative() && dirtyLocal.index == local.lvn().index) {
                return Optional.empty();
            }
            OptionalInt dirtyNameOrdinal = GeneratedVariables.getGeneratedVariableOrdinal(dirtyLocal.name, Type.getType(dirtyLocal.desc));
            if (dirtyNameOrdinal.isEmpty() || local.relative() && ordinal == dirtyNameOrdinal.getAsInt()) {
                return Optional.empty();
            }

            if (cleanLocal.index != dirtyLocal.index && !local.relative()) {
                return Optional.of(new LocalVar(dirtyLocal, ordinal));
            }

            return Optional.empty();
        }

        private static Optional<LocalVariableNode> findReplacementLocal(MethodNode cleanMethod, MethodNode dirtyMethod, LocalVariableNode desired) {
            // Find variable initializer insns
            InsnList desiredInitializerInsns = LocalsAnalyzer.findInitializerInsns(cleanMethod, desired.index);
            return LocalsAnalyzer.findLocalWithInitializer(dirtyMethod, desired.desc, desiredInitializerInsns);
        }
    }
}
