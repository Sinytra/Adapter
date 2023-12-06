package dev.su5ed.sinytra.adapter.patch.transformer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.analysis.InstructionMatcher;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Stream;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public class DynamicInjectorOrdinalPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, OffsetHandler> OFFSET_HANDLERS = Map.of(
        "INVOKE", InvokeOffsetHandler.INSTANCE,
        "RETURN", ReturnOffsetHandler.INSTANCE
    );

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.INJECT);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle injectionPointAnnotation = methodContext.injectionPointAnnotation();
        if (injectionPointAnnotation == null) {
            return Patch.Result.PASS;
        }
        AnnotationValueHandle<Integer> ordinal = injectionPointAnnotation.<Integer>getValue("ordinal").orElse(null);
        if (ordinal == null) {
            return Patch.Result.PASS;
        }

        AnnotationHandle annotationHandle = methodContext.injectionPointAnnotation();
        if (annotationHandle == null) {
            return Patch.Result.PASS;
        }
        OffsetHandler offsetHandler = annotationHandle.<String>getValue("value").map(AnnotationValueHandle::get).map(OFFSET_HANDLERS::get).orElse(null);
        if (offsetHandler == null) {
            return Patch.Result.PASS;
        }
        String value = annotationHandle.<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
        if (offsetHandler.requiresTarget() && value == null) {
            return Patch.Result.PASS;
        }

        ClassLookup cleanClassProvider = context.getEnvironment().getCleanClassLookup();
        Pair<ClassNode, MethodNode> cleanTarget = methodContext.findInjectionTarget(methodContext.methodAnnotation(), context, s -> cleanClassProvider.getClass(s).orElse(null));
        if (cleanTarget == null) {
            return Patch.Result.PASS;
        }

        Pair<ClassNode, MethodNode> dirtyTarget = methodContext.findInjectionTarget(methodContext.methodAnnotation(), context, AdapterUtil::getClassNode);
        if (dirtyTarget == null) {
            return Patch.Result.PASS;
        }

        OptionalInt updatedIndex = offsetHandler.getUpdatedIndex(context, value, cleanTarget, dirtyTarget, ordinal.get());
        if (updatedIndex.isPresent()) {
            int index = updatedIndex.getAsInt();
            LOGGER.info(MIXINPATCH, "Updating injection point ordinal of {}.{} from {} to {}", classNode.name, methodNode.name, ordinal.get(), index);
            ordinal.set(index);
            return Patch.Result.APPLY;
        }
        return Patch.Result.PASS;
    }

    private interface OffsetHandler {
        default boolean requiresTarget() {
            return false;
        }

        OptionalInt getUpdatedIndex(PatchContext context, String target, Pair<ClassNode, MethodNode> cleanTarget, Pair<ClassNode, MethodNode> dirtyTarget, int ordinal);
    }

    private static class InvokeOffsetHandler implements OffsetHandler {
        public static final OffsetHandler INSTANCE = new InvokeOffsetHandler();

        @Override
        public boolean requiresTarget() {
            return true;
        }

        @Override
        public OptionalInt getUpdatedIndex(PatchContext context, String target, Pair<ClassNode, MethodNode> cleanTarget, Pair<ClassNode, MethodNode> dirtyTarget, int ordinal) {
            Multimap<String, MethodInsnNode> cleanCallsMap = MethodCallAnalyzer.getMethodCalls(cleanTarget.getSecond(), new ArrayList<>());
            Multimap<String, MethodInsnNode> dirtyCallsMap = MethodCallAnalyzer.getMethodCalls(dirtyTarget.getSecond(), new ArrayList<>());

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
        public OptionalInt getUpdatedIndex(PatchContext context, String target, Pair<ClassNode, MethodNode> cleanTarget, Pair<ClassNode, MethodNode> dirtyTarget, int ordinal) {
            List<AbstractInsnNode> cleanReturnInsns = findReturnInsns(cleanTarget.getSecond());
            List<AbstractInsnNode> dirtyReturnInsns = findReturnInsns(dirtyTarget.getSecond());

            if (dirtyReturnInsns.size() > cleanReturnInsns.size() && ordinal < cleanReturnInsns.size()) {
                AbstractInsnNode cleanInsn = cleanReturnInsns.get(ordinal);
                InstructionMatcher original = new InstructionMatcher(cleanInsn, findReturnPrecedingInsns(cleanInsn), List.of());
                List<InstructionMatcher> dirtyMatchers = dirtyReturnInsns.stream()
                    .map(i -> new InstructionMatcher(i, findReturnPrecedingInsns(i), List.of()))
                    .toList();
                List<InstructionMatcher> matches = dirtyMatchers.stream()
                    .filter(original::test)
                    .toList();
                if (matches.size() == 1) {
                    return OptionalInt.of(dirtyMatchers.indexOf(matches.get(0)));
                }
            }
            return OptionalInt.empty();
        }

        private static List<AbstractInsnNode> findReturnPrecedingInsns(AbstractInsnNode insn) {
            List<AbstractInsnNode> insns = Stream.iterate(insn.getPrevious(), Objects::nonNull, AbstractInsnNode::getPrevious)
                .filter(i -> !(i instanceof FrameNode) && !(i instanceof LineNumberNode))
                .takeWhile(i -> !RETURN_OPCODES.contains(i.getOpcode()))
                .toList();
            return Lists.reverse(insns).subList(0, 6);
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
}
