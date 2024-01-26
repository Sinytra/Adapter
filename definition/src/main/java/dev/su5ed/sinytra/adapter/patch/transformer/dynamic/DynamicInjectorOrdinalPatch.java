package dev.su5ed.sinytra.adapter.patch.transformer.dynamic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.analysis.InstructionMatcher;
import dev.su5ed.sinytra.adapter.patch.analysis.LocalVarAnalyzer;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.api.*;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.util.GeneratedVariables;
import dev.su5ed.sinytra.adapter.patch.util.InsnComparator;
import dev.su5ed.sinytra.adapter.patch.util.LocalVariableLookup;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.*;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public class DynamicInjectorOrdinalPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, OffsetHandler> OFFSET_HANDLERS = Map.of(
        "INVOKE", InvokeOffsetHandler.INSTANCE,
        "RETURN", ReturnOffsetHandler.INSTANCE
    );

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.INJECT, MixinConstants.MODIFY_VAR);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        List<HandlerInstance> offsetHandlers = getOffsetHandlers(methodContext);
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
        for (HandlerInstance instance : offsetHandlers) {
            AnnotationValueHandle<Integer> ordinal = instance.ordinal();
            OptionalInt updatedOrdinal = instance.handler().getUpdatedOrdinal(context, methodNode, instance.target(), cleanTarget, dirtyTarget, ordinal.get());
            if (updatedOrdinal.isPresent()) {
                int index = updatedOrdinal.getAsInt();
                LOGGER.info(MIXINPATCH, "Updating injection point ordinal of {}.{} from {} to {}", classNode.name, methodNode.name, ordinal.get(), index);
                ordinal.set(index);
                applied = true;
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private static List<HandlerInstance> getOffsetHandlers(MethodContext methodContext) {
        List<HandlerInstance> handlers = new ArrayList<>();
        AnnotationHandle annotation = methodContext.injectionPointAnnotationOrThrow();

        annotation.<Integer>getValue("ordinal").ifPresent(atOrdinal -> {
            String target = annotation.<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
            annotation.<String>getValue("value")
                .map(AnnotationValueHandle::get)
                .map(OFFSET_HANDLERS::get)
                .filter(handler -> !handler.requiresTarget() || target != null)
                .ifPresent(h -> handlers.add(new HandlerInstance(h, target, atOrdinal)));
        });
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.MODIFY_VAR)) {
            methodContext.methodAnnotation().<Integer>getValue("ordinal")
                .ifPresent(varOrdinal -> handlers.add(new HandlerInstance(ModifyVariableOffsetHandler.INSTANCE, null, varOrdinal)));
        }
        return handlers;
    }

    private record HandlerInstance(OffsetHandler handler, String target, AnnotationValueHandle<Integer> ordinal) {
    }

    private interface OffsetHandler {
        default boolean requiresTarget() {
            return false;
        }

        OptionalInt getUpdatedOrdinal(PatchContext context, MethodNode methodNode, String target, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, int ordinal);
    }

    private static class InvokeOffsetHandler implements OffsetHandler {
        public static final OffsetHandler INSTANCE = new InvokeOffsetHandler();

        @Override
        public boolean requiresTarget() {
            return true;
        }

        @Override
        public OptionalInt getUpdatedOrdinal(PatchContext context, MethodNode methodNode, String target, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, int ordinal) {
            Multimap<String, MethodInsnNode> cleanCallsMap = MethodCallAnalyzer.getMethodCalls(cleanTarget.methodNode(), new ArrayList<>());
            Multimap<String, MethodInsnNode> dirtyCallsMap = MethodCallAnalyzer.getMethodCalls(dirtyTarget.methodNode(), new ArrayList<>());

            String cleanValue = context.remap(target);
            Collection<? extends AbstractInsnNode> cleanCalls = cleanCallsMap.get(cleanValue);
            String dirtyValue = context.remap(target);
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
                        return OptionalInt.of(dirtyMatchers.indexOf(matches.get(0)));
                    }
                }
            }
            return OptionalInt.empty();
        }
    }

    private static class ReturnOffsetHandler implements OffsetHandler {
        public static final OffsetHandler INSTANCE = new ReturnOffsetHandler();
        private static final Set<Integer> RETURN_OPCODES = Set.of(Opcodes.RETURN, Opcodes.ARETURN, Opcodes.IRETURN, Opcodes.FRETURN, Opcodes.DRETURN, Opcodes.LRETURN);

        @Override
        public OptionalInt getUpdatedOrdinal(PatchContext context, MethodNode methodNode, String target, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, int ordinal) {
            List<AbstractInsnNode> cleanReturnInsns = findReturnInsns(cleanTarget.methodNode());
            List<AbstractInsnNode> dirtyReturnInsns = findReturnInsns(dirtyTarget.methodNode());

            if (ordinal < cleanReturnInsns.size()) {
                AbstractInsnNode cleanInsn = cleanReturnInsns.get(ordinal);
                InstructionMatcher original = new InstructionMatcher(cleanInsn, findReturnPrecedingInsns(cleanInsn), List.of());
                List<InstructionMatcher> dirtyMatchers = dirtyReturnInsns.stream()
                    .map(i -> new InstructionMatcher(i, findReturnPrecedingInsns(i), List.of()))
                    .toList();
                List<InstructionMatcher> matches = dirtyMatchers.stream()
                    .filter(m -> original.test(m, InsnComparator.IGNORE_VAR_INDEX))
                    .toList();
                if (matches.size() == 1) {
                    return OptionalInt.of(dirtyMatchers.indexOf(matches.get(0)));
                }
            }
            return OptionalInt.empty();
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

    private static class ModifyVariableOffsetHandler implements OffsetHandler {
        private static final OffsetHandler INSTANCE = new ModifyVariableOffsetHandler();

        @Override
        public OptionalInt getUpdatedOrdinal(PatchContext context, MethodNode methodNode, String target, MethodContext.TargetPair cleanTarget, MethodContext.TargetPair dirtyTarget, int ordinal) {
            Type[] args = Type.getArgumentTypes(methodNode.desc);
            if (args.length < 1) {
                return OptionalInt.empty();
            }
            Type targetType = args[0];
            // Gradually expand supported types over time as necessary
            if (targetType != Type.BOOLEAN_TYPE && targetType != Type.INT_TYPE) {
                return OptionalInt.empty();
            }
            List<LocalVariableNode> cleanLocals = cleanTarget.methodNode().localVariables.stream()
                .filter(l -> Type.getType(l.desc) == targetType)
                .sorted(Comparator.comparingInt(l -> l.index))
                .toList();
            if (cleanLocals.size() <= ordinal) {
                return OptionalInt.empty();
            }
            LocalVariableNode cleanLocal = cleanLocals.get(ordinal);
            if (!GeneratedVariables.isGeneratedVariableName(cleanLocal.name, Type.getType(cleanLocal.desc))) {
                return OptionalInt.empty();
            }

            LocalVariableLookup dirtyVarLookup = new LocalVariableLookup(dirtyTarget.methodNode());
            List<LocalVariableNode> dirtyLocals = dirtyVarLookup.getForType(targetType);
            if (cleanLocals.size() != dirtyLocals.size() || dirtyLocals.size() <= ordinal) {
                return findReplacementLocal(cleanTarget.methodNode(), dirtyTarget.methodNode(), cleanLocal)
                    .map(dirtyVarLookup::getTypedOrdinal)
                    .orElseGet(OptionalInt::empty);
            }
            LocalVariableNode dirtyLocal = dirtyLocals.get(ordinal);
            OptionalInt dirtyNameOrdinal = GeneratedVariables.getGeneratedVariableOrdinal(dirtyLocal.name, Type.getType(dirtyLocal.desc));
            if (dirtyNameOrdinal.isEmpty() || ordinal == dirtyNameOrdinal.getAsInt()) {
                return OptionalInt.empty();
            }
            List<LocalVariableNode> actual = dirtyLocals.stream()
                .filter(lvn -> GeneratedVariables.getGeneratedVariableOrdinal(lvn.name, Type.getType(lvn.desc)).orElse(-1) == ordinal)
                .toList();
            if (actual.size() == 1) {
                return OptionalInt.of(dirtyLocals.indexOf(actual.get(0)));
            }
            return OptionalInt.empty();
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
