package org.sinytra.adapter.next.pipeline.resolver.special;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ctx.PatchContext;
import org.sinytra.adapter.next.env.ctx.TargetPair;
import org.sinytra.adapter.next.env.util.MixinAnnotations;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.Keys;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.next.pipeline.resolver.Resolver;
import org.sinytra.adapter.patch.analysis.InsnComparator;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.patch.analysis.method.MethodInsnMatcher;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.GeneratedVariables;
import org.sinytra.adapter.patch.util.SingleValueHandle;

import java.util.*;
import java.util.function.Function;

import static org.sinytra.adapter.next.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.next.env.util.MixinAnnotationConstants.AT_VAL_RETURN;

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

        TargetPair cleanTarget = recipe.getCleanTarget();
        if (cleanTarget == null) return ResolutionResult.pass();

        TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (dirtyTarget == null) return ResolutionResult.pass();

        boolean applied = false;
        MutableConfiguration merged = MutableConfiguration.create();
        for (HandlerInstance<?, ?> instance : offsetHandlers) {
            Configuration config = instance.apply(context, cleanTarget, dirtyTarget);
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

        if (context.methodAnnotation().matchesDesc(MixinAnnotations.MODIFY_VAR)) {
            LocalVariableLookup cleanTable = recipe.cleanLocalsTable();
            if (cleanTable != null) {
                // Handle modified ordinals
                cleanConfig.getProperty(Keys.ORDINAL)
                    .flatMap(ordinal -> cleanTable.getByTypedOrdinal(returnType, ordinal)
                        .flatMap(lvn -> cleanTable.getTypedOrdinal(lvn).map(o -> new LocalVar(lvn, o, true)))
                        .map(local -> new HandlerInstance<>(ModifyVariableOffsetHandler.INSTANCE, local, var ->
                            MutableConfiguration.create()
                                .setProperty(Keys.ORDINAL, var.ordinal())
                        )))
                    // Handle modified indexes
                    .or(() -> cleanConfig.getProperty(Keys.INDEX)
                        .flatMap(i -> Optional.ofNullable(cleanTable.getByIndexOrNull(i))
                            .flatMap(lvn -> cleanTable.getTypedOrdinal(lvn).map(o -> new LocalVar(lvn, o, false)))
                            .map(local -> new HandlerInstance<>(ModifyVariableOffsetHandler.INSTANCE, local, var ->
                                MutableConfiguration.create()
                                    .setProperty(Keys.INDEX, var.lvn().index)
                            ))
                        ))
                    .ifPresent(handlers::add);
            }
        }

        return handlers;
    }

    private interface UpdateHandler<T, U> {
        Optional<U> apply(MixinContext mixinContext, TargetPair cleanTarget, TargetPair dirtyTarget, T context);
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
        public Configuration apply(MixinContext mixinContext, TargetPair cleanTarget, TargetPair dirtyTarget) {
            Optional<U> updatedValue = this.handler.apply(mixinContext, cleanTarget, dirtyTarget, this.context);
            if (updatedValue.isPresent()) {
                U value = updatedValue.get();
//                methodContext.recordAudit(transform, "Update injection point ordinal from %s to %s", this.context, value);
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
        public Optional<Integer> apply(MixinContext mixinContext, TargetPair cleanTarget, TargetPair dirtyTarget, Context context) {
            String target = context.target();
            int ordinal = context.ordinal();

            Multimap<String, MethodInsnNode> cleanCallsMap = MethodAnalyzer.getMethodCalls(cleanTarget.methodNode(), new ArrayList<>());
            Multimap<String, MethodInsnNode> dirtyCallsMap = MethodAnalyzer.getMethodCalls(dirtyTarget.methodNode(), new ArrayList<>());

            PatchContext patchContext = mixinContext.patchContext();
            String cleanValue = patchContext.remap(target);
            Collection<? extends AbstractInsnNode> cleanCalls = cleanCallsMap.get(cleanValue);
            String dirtyValue = patchContext.remap(target);
            Collection<? extends AbstractInsnNode> dirtyCalls = dirtyCallsMap.get(dirtyValue);

            if (cleanCalls.size() != dirtyCalls.size()) {
                List<InstructionMatcher> cleanMatchers = cleanCalls.stream().map(MethodInsnMatcher::findSurroundingInstructions).toList();
                List<InstructionMatcher> dirtyMatchers = dirtyCalls.stream().map(MethodInsnMatcher::findSurroundingInstructions).toList();

                if (ordinal >= 0 && ordinal < cleanMatchers.size()) {
                    InstructionMatcher original = cleanMatchers.get(ordinal);
                    List<InstructionMatcher> matches = dirtyMatchers.stream()
                        .filter(original::test)
                        .toList();
                    if (matches.size() == 1) {
                        return Optional.of(dirtyMatchers.indexOf(matches.getFirst()));
                    }
                }
            }

            return Optional.empty();
        }
    }

    private static class ReturnOffsetHandler implements OffsetUpdateHandler {
        public static final OffsetUpdateHandler INSTANCE = new ReturnOffsetHandler();
        private static final Set<Integer> RETURN_OPCODES = Set.of(Opcodes.RETURN, Opcodes.ARETURN, Opcodes.IRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.LRETURN);

        @Override
        public Optional<Integer> apply(MixinContext mixinContext, TargetPair cleanTarget, TargetPair dirtyTarget, Context context) {
            int ordinal = context.ordinal();

            List<AbstractInsnNode> cleanReturnInsns = findReturnInsns(cleanTarget.methodNode());
            List<AbstractInsnNode> dirtyReturnInsns = findReturnInsns(dirtyTarget.methodNode());

            if (ordinal < cleanReturnInsns.size() && cleanReturnInsns.size() != dirtyReturnInsns.size()) {
                AbstractInsnNode cleanInsn = cleanReturnInsns.get(ordinal);
                InstructionMatcher original = new InstructionMatcher(cleanInsn, findReturnPrecedingInsns(cleanInsn), List.of());
                List<InstructionMatcher> dirtyMatchers = dirtyReturnInsns.stream()
                    .map(i -> new InstructionMatcher(i, findReturnPrecedingInsns(i), List.of()))
                    .toList();
                List<InstructionMatcher> matches = dirtyMatchers.stream()
                    .filter(m -> original.test(m, InsnComparator.IGNORE_VAR_INDEX))
                    .toList();
                if (matches.size() == 1) {
                    return Optional.of(dirtyMatchers.indexOf(matches.getFirst()));
                }
            }

            return Optional.empty();
        }

        private static List<AbstractInsnNode> findReturnPrecedingInsns(AbstractInsnNode insn) {
            List<AbstractInsnNode> insns = new ArrayList<>();
            int maxSize = 6;
            for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                if (insns.size() >= maxSize) {
                    break;
                }
                if (RETURN_OPCODES.contains(prev.getOpcode())) {
                    break;
                }
                if (prev instanceof FrameNode || prev instanceof LineNumberNode || prev instanceof LabelNode) {
                    continue;
                }
                insns.addFirst(prev);
            }
            return insns;
        }

        private static List<AbstractInsnNode> findReturnInsns(MethodNode methodNode) {
            ImmutableList.Builder<AbstractInsnNode> insns = ImmutableList.builder();
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (RETURN_OPCODES.contains(insn.getOpcode())) {
                    insns.add(insn);
                }
            }
            return insns.build();
        }
    }

    private static class ModifyVariableOffsetHandler implements UpdateHandler<LocalVar, LocalVar> {
        private static final ModifyVariableOffsetHandler INSTANCE = new ModifyVariableOffsetHandler();

        @Override
        public Optional<LocalVar> apply(MixinContext mixinContext, TargetPair cleanTarget, TargetPair dirtyTarget, LocalVar local) {
            MethodNode methodNode = mixinContext.methodNode();

            Type[] args = Type.getArgumentTypes(methodNode.desc);
            if (args.length < 1) {
                return Optional.empty();
            }

            Type targetType = args[0];
            // Gradually expand supported types over time as necessary
            if (targetType != Type.BOOLEAN_TYPE && targetType != Type.INT_TYPE && targetType != Type.FLOAT_TYPE) {
                return Optional.empty();
            }

            if (mixinContext.methodAnnotation().getValue("slice").isPresent() && local.relative()) {
                return Optional.empty();
            }

            return tryFindUpdatedIndex(targetType, cleanTarget, dirtyTarget, local)
                .or(() -> tryFindSyntheticVariableIndex(mixinContext, methodNode, cleanTarget, dirtyTarget, local));
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
        private static Optional<LocalVar> tryFindSyntheticVariableIndex(MixinContext mixinContext, MethodNode methodNode, TargetPair cleanTarget, TargetPair dirtyTarget, LocalVar local) {
            int ordinal = local.ordinal();
            Type variableType = Type.getReturnType(methodNode.desc);
            LocalVariableLookup cleanTable = new LocalVariableLookup(cleanTarget.methodNode());
            LocalVariableLookup dirtyTable = new LocalVariableLookup(dirtyTarget.methodNode());
            if (cleanTable.getForType(variableType).size() == dirtyTable.getForType(variableType).size()) {
                List<LocalVariableNode> available = dirtyTable.getForType(variableType);
                if (available.size() > ordinal) {
                    int variableIndex = available.get(ordinal).index;
                    AbstractInsnNode cleanInsn = mixinContext.methods().findInjectionTargetInsn(cleanTarget);
                    AbstractInsnNode dirtyInsn = mixinContext.methods().findInjectionTargetInsn(dirtyTarget);
                    if (cleanInsn != null && dirtyInsn != null) {
                        for (AbstractInsnNode insn = cleanInsn; insn != null; insn = insn.getNext()) {
                            if (insn instanceof LabelNode) {
                                break;
                            }
                            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                            if (handle != null && handle.get() == variableIndex) {
                                // We found out the variable is used right after our injection point
                                // Now let's check if it its index remain the same in the dirty target
                                List<SingleValueHandle<Integer>> dirtyVars = getUsedVariablesInLabel(dirtyInsn, insn.getOpcode());
                                if (dirtyVars.size() == 1) {
                                    int dirtyIndex = dirtyVars.getFirst().get();
                                    if (dirtyIndex != variableIndex) {
                                        // FIXME Cannot use set
                                        mixinContext.methodAnnotation().<Boolean>getValue("argsOnly")
                                            .ifPresent(h -> h.set(false));

                                        // Find new ordinal by index
                                        LocalVariableNode lvn = dirtyTable.getByIndex(dirtyIndex);
                                        return dirtyTable.getTypedOrdinal(lvn)
                                            .map(o -> new LocalVar(lvn, o));
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
            for (AbstractInsnNode insn = start; insn != null; insn = insn.getNext()) {
                if (insn instanceof LabelNode) {
                    break;
                }
                if (insn.getOpcode() == opcode) {
                    SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                    if (handle != null) {
                        list.add(handle);
                    }
                }
            }
            return list;
        }

        private static Optional<LocalVar> tryFindUpdatedIndex(Type targetType, TargetPair cleanTarget, TargetPair dirtyTarget, LocalVar local) {
            int ordinal = local.ordinal();
            List<LocalVariableNode> cleanLocals = cleanTarget.methodNode().localVariables.stream()
                .filter(l -> Type.getType(l.desc) == targetType)
                .sorted(Comparator.comparingInt(l -> l.index))
                .toList();
            if (cleanLocals.size() <= ordinal) {
                return Optional.empty();
            }
            LocalVariableNode cleanLocal = cleanLocals.get(ordinal);
            if (!GeneratedVariables.isGeneratedVariableName(cleanLocal.name, Type.getType(cleanLocal.desc))) {
                return Optional.empty();
            }

            LocalVariableLookup dirtyVarLookup = new LocalVariableLookup(dirtyTarget.methodNode());
            List<LocalVariableNode> dirtyLocals = dirtyVarLookup.getForType(targetType);
            if (cleanLocals.size() != dirtyLocals.size() || dirtyLocals.size() <= ordinal) {
                return findReplacementLocal(cleanTarget.methodNode(), dirtyTarget.methodNode(), cleanLocal)
                    .flatMap(var -> dirtyVarLookup.getTypedOrdinal(var).map(o -> new LocalVar(var, o)));
            }
            LocalVariableNode dirtyLocal = dirtyLocals.get(ordinal);
            if (!local.relative() && dirtyLocal.index == local.lvn().index) {
                return Optional.empty();
            }
            OptionalInt dirtyNameOrdinal = GeneratedVariables.getGeneratedVariableOrdinal(dirtyLocal.name, Type.getType(dirtyLocal.desc));
            if (dirtyNameOrdinal.isEmpty() || local.relative() && ordinal == dirtyNameOrdinal.getAsInt()) {
                return Optional.empty();
            }

            if (cleanLocal.index != dirtyLocal.index && !local.relative()) {
                return Optional.of(new LocalVar(dirtyLocal, dirtyLocals.indexOf(dirtyLocal)));
            }

            return Optional.empty();
        }

        private static Optional<LocalVariableNode> findReplacementLocal(MethodNode cleanMethod, MethodNode dirtyMethod, LocalVariableNode desired) {
            // Find variable initializer insns
            InsnList desiredInitializerInsns = LocalVarAnalyzer.findInitializerInsns(cleanMethod, desired.index);
            // Get all matching variables
            List<LocalVariableNode> matches = dirtyMethod.localVariables.stream()
                .filter(lvn -> desired.desc.equals(lvn.desc))
                .filter(lvn -> {
                    InsnList insns = LocalVarAnalyzer.findInitializerInsns(dirtyMethod, lvn.index);
                    return InstructionMatcher.test(desiredInitializerInsns, insns);
                })
                .toList();
            // Succeed on one exact match
            return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
        }
    }
}
