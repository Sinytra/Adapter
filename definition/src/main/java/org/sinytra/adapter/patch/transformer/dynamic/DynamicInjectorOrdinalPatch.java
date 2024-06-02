package org.sinytra.adapter.patch.transformer.dynamic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.analysis.*;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.GeneratedVariables;
import org.sinytra.adapter.patch.util.SingleValueHandle;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;

public class DynamicInjectorOrdinalPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, OffsetUpdateHandler> OFFSET_HANDLERS = Map.of(
        "INVOKE", InvokeOffsetHandler.INSTANCE,
        "RETURN", ReturnOffsetHandler.INSTANCE
    );

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.INJECT, MixinConstants.MODIFY_VAR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        Type returnType = Type.getReturnType(methodNode.desc);
        List<HandlerInstance<?, ?>> offsetHandlers = getOffsetHandlers(methodContext, returnType);
        if (offsetHandlers.isEmpty()) {
            return Patch.Result.PASS;
        }
        MethodContext.TargetPair cleanTarget = methodContext.findCleanInjectionTarget();
        if (cleanTarget == null) {
            return Patch.Result.PASS;
        }
        MethodContext.TargetPair dirtyTarget = methodContext.findDirtyInjectionTarget();
        if (dirtyTarget == null) {
            return Patch.Result.PASS;
        }

        boolean applied = false;
        for (HandlerInstance<?, ?> instance : offsetHandlers) {
            applied |= instance.apply(methodContext, classNode, methodNode, cleanTarget, dirtyTarget);
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private static List<HandlerInstance<?, ?>> getOffsetHandlers(MethodContext methodContext, Type returnType) {
        List<HandlerInstance<?, ?>> handlers = new ArrayList<>();
        AnnotationHandle annotation = methodContext.injectionPointAnnotationOrThrow();

        annotation.<Integer>getValue("ordinal").ifPresent(atOrdinal -> {
            String target = annotation.<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
            annotation.<String>getValue("value")
                .map(AnnotationValueHandle::get)
                .map(OFFSET_HANDLERS::get)
                .filter(handler -> !handler.requiresTarget() || target != null)
                .ifPresent(h -> handlers.add(new HandlerInstance<>(h, new OffsetUpdateHandler.Context(target, atOrdinal.get()), atOrdinal::set)));
        });
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_VAR)) {
            LocalVariableLookup cleanTable = methodContext.cleanLocalsTable();
            if (cleanTable != null) {
                // Handle modified ordinals
                methodContext.methodAnnotation().<Integer>getValue("ordinal")
                    .flatMap(ordinal -> cleanTable.getByTypedOrdinal(returnType, ordinal.get())
                        .flatMap(lvn -> cleanTable.getTypedOrdinal(lvn).map(o -> new LocalVar(lvn, o, true)))
                        .map(local -> new HandlerInstance<>(ModifyVariableOffsetHandler.INSTANCE, local, var -> ordinal.set(var.ordinal()))))
                    // Handle modified indexes
                    .or(() -> methodContext.methodAnnotation().<Integer>getValue("index")
                        .flatMap(index -> Optional.ofNullable(cleanTable.getByIndexOrNull(index.get()))
                            .flatMap(lvn -> cleanTable.getTypedOrdinal(lvn).map(o -> new LocalVar(lvn, o, false)))
                            .map(local -> new HandlerInstance<>(ModifyVariableOffsetHandler.INSTANCE, local, var -> index.set(var.lvn().index)))))
                    .ifPresent(handlers::add);
            }
        }
        return handlers;
    }

    private interface UpdateHandler<T, U> {
        Optional<U> apply(MethodContext methodContext, ClassNode classNode, MethodNode methodNode, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, T context);
    }

    private interface OffsetUpdateHandler extends UpdateHandler<OffsetUpdateHandler.Context, Integer> {
        record Context(@Nullable String target, int ordinal) {}

        default boolean requiresTarget() {
            return false;
        }
    }

    private record LocalVar(LocalVariableNode lvn, int ordinal, boolean relative) {
        public LocalVar(LocalVariableNode lvn, int ordinal) {
            this(lvn, ordinal, false);
        }
    }

    private record HandlerInstance<T, U>(UpdateHandler<T, U> handler, T context, Consumer<U> applicator) {
        public boolean apply(MethodContext methodContext, ClassNode classNode, MethodNode methodNode, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget) {
            Optional<U> updatedValue = this.handler.apply(methodContext, classNode, methodNode, cleanTarget, dirtyTarget, this.context);
            if (updatedValue.isPresent()) {
                U value = updatedValue.get();
                LOGGER.info(PatchInstance.MIXINPATCH, "Updating injection point ordinal of {}.{} from {} to {}", classNode.name, methodNode.name, this.context, value);
                this.applicator.accept(value);
                return true;
            }
            return false;
        }
    }

    private static class InvokeOffsetHandler implements OffsetUpdateHandler {
        public static final InvokeOffsetHandler INSTANCE = new InvokeOffsetHandler();

        @Override
        public boolean requiresTarget() {
            return true;
        }

        @Override
        public Optional<Integer> apply(MethodContext methodContext, ClassNode classNode, MethodNode methodNode, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, Context context) {
            String target = context.target();
            int ordinal = context.ordinal();

            Multimap<String, MethodInsnNode> cleanCallsMap = MethodCallAnalyzer.getMethodCalls(cleanTarget.methodNode(), new ArrayList<>());
            Multimap<String, MethodInsnNode> dirtyCallsMap = MethodCallAnalyzer.getMethodCalls(dirtyTarget.methodNode(), new ArrayList<>());

            PatchContext patchContext = methodContext.patchContext();
            String cleanValue = patchContext.remap(target);
            Collection<? extends AbstractInsnNode> cleanCalls = cleanCallsMap.get(cleanValue);
            String dirtyValue = patchContext.remap(target);
            Collection<? extends AbstractInsnNode> dirtyCalls = dirtyCallsMap.get(dirtyValue);

            if (cleanCalls.size() != dirtyCalls.size()) {
                int insnRange = 5;
                List<InstructionMatcher> cleanMatchers = cleanCalls.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();
                List<InstructionMatcher> dirtyMatchers = dirtyCalls.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();

                if (ordinal >= 0 && ordinal < cleanMatchers.size()) {
                    InstructionMatcher original = cleanMatchers.get(ordinal);
                    List<InstructionMatcher> matches = dirtyMatchers.stream()
                        .filter(original::test)
                        .toList();
                    if (matches.size() == 1) {
                        return Optional.of(dirtyMatchers.indexOf(matches.get(0)));
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
        public Optional<Integer> apply(MethodContext methodContext, ClassNode classNode, MethodNode methodNode, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, Context context) {
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
                    return Optional.of(dirtyMatchers.indexOf(matches.get(0)));
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
                insns.add(0, prev);
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
        public Optional<LocalVar> apply(MethodContext methodContext, ClassNode classNode, MethodNode methodNode, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, LocalVar local) {
            Type[] args = Type.getArgumentTypes(methodNode.desc);
            if (args.length < 1) {
                return Optional.empty();
            }
            Type targetType = args[0];
            // Gradually expand supported types over time as necessary
            if (targetType != Type.BOOLEAN_TYPE && targetType != Type.INT_TYPE && targetType != Type.FLOAT_TYPE) {
                return Optional.empty();
            }
            if (methodContext.methodAnnotation().getValue("slice").isPresent() && local.relative()) {
                return Optional.empty();
            }
            return tryFindUpdatedIndex(targetType, cleanTarget, dirtyTarget, local)
                .or(() -> tryFindSyntheticVariableIndex(methodContext, methodNode, cleanTarget, dirtyTarget, local));
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
        private static Optional<LocalVar> tryFindSyntheticVariableIndex(MethodContext methodContext, MethodNode methodNode, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, LocalVar local) {
            int ordinal = local.ordinal();
            Type variableType = Type.getReturnType(methodNode.desc);
            LocalVariableLookup cleanTable = new LocalVariableLookup(cleanTarget.methodNode());
            LocalVariableLookup dirtyTable = new LocalVariableLookup(dirtyTarget.methodNode());
            if (cleanTable.getForType(variableType).size() == dirtyTable.getForType(variableType).size()) {
                List<LocalVariableNode> available = dirtyTable.getForType(variableType);
                if (available.size() > ordinal) {
                    int variableIndex = available.get(ordinal).index;
                    List<AbstractInsnNode> cleanInsns = methodContext.findInjectionTargetInsns(cleanTarget);
                    List<AbstractInsnNode> dirtyInsns = methodContext.findInjectionTargetInsns(dirtyTarget);
                    if (cleanInsns.size() == 1 && dirtyInsns.size() == 1) {
                        for (AbstractInsnNode insn = cleanInsns.get(0); insn != null; insn = insn.getNext()) {
                            if (insn instanceof LabelNode) {
                                break;
                            }
                            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                            if (handle != null && handle.get() == variableIndex) {
                                // We found out the variable is used right after our injection point
                                // Now let's check if it its index remain the same in the dirty target
                                List<SingleValueHandle<Integer>> dirtyVars = getUsedVariablesInLabel(dirtyInsns.get(0), insn.getOpcode());
                                if (dirtyVars.size() == 1) {
                                    int dirtyIndex = dirtyVars.get(0).get();
                                    if (dirtyIndex != variableIndex) {
                                        methodContext.methodAnnotation().<Boolean>getValue("argsOnly")
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

        private static Optional<LocalVar> tryFindUpdatedIndex(Type targetType, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, LocalVar local) {
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
            return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
        }
    }
}
