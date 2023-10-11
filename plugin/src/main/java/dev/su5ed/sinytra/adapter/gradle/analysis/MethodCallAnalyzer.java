package dev.su5ed.sinytra.adapter.gradle.analysis;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import dev.su5ed.sinytra.adapter.patch.Patch;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public class MethodCallAnalyzer {
    private static final UnaryOperator<AbstractInsnNode> FORWARD = AbstractInsnNode::getNext;
    private static final UnaryOperator<AbstractInsnNode> BACKWARDS = AbstractInsnNode::getPrevious;

    private static final Logger LOGGER = LoggerFactory.getLogger("MethodCallAnalyzer");

    public static void findReplacedMethodCalls(ClassNode dirtyNode, Map<MethodNode, MethodNode> cleanToDirty, List<Patch> patches, TraceCallback trace) {
        cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            int callAnalysisLimit = 2;
            int insnRange = 5;
            List<String> cleanCallOrder = new ArrayList<>();
            List<String> dirtyCallOrder = new ArrayList<>();
            Multimap<String, MethodInsnNode> cleanCalls = getMethodCalls(cleanMethod, cleanCallOrder);
            Multimap<String, MethodInsnNode> dirtyCalls = getMethodCalls(dirtyMethod, dirtyCallOrder);

            dirtyCalls.asMap().forEach((qualifier, dirtyList) -> {
                Collection<MethodInsnNode> cleanList = cleanCalls.get(qualifier);
                if (cleanList.size() != dirtyList.size() && cleanList.size() <= callAnalysisLimit) {
                    List<InstructionMatcher> cleanMatchers = cleanList.stream().map(i -> findSurroundingInstructions(i, insnRange)).toList();
                    List<InstructionMatcher> dirtyMatchers = dirtyList.stream().map(i -> findSurroundingInstructions(i, insnRange)).toList();

                    List<InstructionMatcher> missing = identifyMissingCalls(cleanMatchers, dirtyMatchers);

                    for (InstructionMatcher matcher : missing) {
                        String original = getCallQualifier(matcher.insn());
                        String replacement = matcher.findReplacement(cleanCallOrder, dirtyCallOrder);
                        if (replacement != null && !replacement.equals(original)) {
                            trace.logHeader();
                            LOGGER.info("Replacing method call in {} to {}.{} with {}", dirtyMethod.name, matcher.insn().owner, matcher.insn().name, replacement);
                            Patch patch = Patch.builder()
                                .targetClass(dirtyNode.name)
                                .targetMethod(dirtyMethod.name + dirtyMethod.desc)
                                .targetInjectionPoint(original)
                                .modifyInjectionPoint(replacement)
                                .targetMixinType(Patch.INJECT)
                                .build();
                            patches.add(patch);
                        }
                    }
                }
            });
        });
    }

    private static List<InstructionMatcher> identifyMissingCalls(List<InstructionMatcher> cleanCalls, List<InstructionMatcher> dirtyCalls) {
        List<InstructionMatcher> activeCleanCalls = new ArrayList<>(cleanCalls);
        List<InstructionMatcher> activeDirtyCalls = new ArrayList<>(dirtyCalls);

        outer:
        for (Iterator<InstructionMatcher> iterator = activeCleanCalls.iterator(); iterator.hasNext(); ) {
            InstructionMatcher cleanMatcher = iterator.next();
            for (InstructionMatcher dirtyMatcher : activeDirtyCalls) {
                if (cleanMatcher.test(dirtyMatcher)) {
                    iterator.remove();
                    activeDirtyCalls.remove(dirtyMatcher);
                    continue outer;
                }
            }
        }

        return activeCleanCalls;
    }

    private static Multimap<String, MethodInsnNode> getMethodCalls(MethodNode node, List<String> callOrder) {
        ImmutableMultimap.Builder<String, MethodInsnNode> calls = ImmutableMultimap.builder();
        for (AbstractInsnNode insn : node.instructions) {
            if (insn instanceof MethodInsnNode minsn) {
                String qualifier = getCallQualifier(minsn);
                calls.put(qualifier, minsn);
                callOrder.add(qualifier);
            }
        }
        return calls.build();
    }

    private static InstructionMatcher findSurroundingInstructions(MethodInsnNode insn, int range) {
        LabelNode previousLabel = findFirstInsn(insn, LabelNode.class, BACKWARDS);
        LabelNode nextLabel = findFirstInsn(insn, LabelNode.class, FORWARD);

        List<AbstractInsnNode> previousInsns = getInsns(previousLabel, range, BACKWARDS);
        List<AbstractInsnNode> nextInsns = getInsns(nextLabel, range, FORWARD);

        return new InstructionMatcher(insn, previousInsns, nextInsns);
    }

    private static List<AbstractInsnNode> getInsns(AbstractInsnNode root, int range, UnaryOperator<AbstractInsnNode> operator) {
        return Stream.iterate(root, Objects::nonNull, operator)
            .filter(insn -> !(insn instanceof FrameNode) && !(insn instanceof LineNumberNode))
            .limit(range)
            .toList();
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends AbstractInsnNode> T findFirstInsn(AbstractInsnNode insn, Class<T> type, UnaryOperator<AbstractInsnNode> operator) {
        return (T) Stream.iterate(insn, Objects::nonNull, operator)
            .filter(type::isInstance)
            .findFirst()
            .orElse(null);
    }

    public static String getCallQualifier(MethodInsnNode insn) {
        return Type.getObjectType(insn.owner).getDescriptor() + insn.name + insn.desc;
    }

    private static <T> int count(List<T> list, T item) {
        return (int) list.stream().filter(item::equals).count();
    }

    record InstructionMatcher(@TestOnly MethodInsnNode insn, List<AbstractInsnNode> before, List<AbstractInsnNode> after) {
        @Nullable
        public String findReplacement(List<String> cleanCallOrder, List<String> dirtyCallOrder) {
            MethodInsnNode previousMethodCall = findFirstInsn(this.before.get(0), MethodInsnNode.class, BACKWARDS);
            if (previousMethodCall == null) {
                return null;
            }
            String previousCallQualifier = getCallQualifier(previousMethodCall);
            int previousCallIndex = dirtyCallOrder.indexOf(previousCallQualifier);
            if (previousCallIndex == -1) {
                return null;
            }
            int previousDirtyCount = count(dirtyCallOrder, previousCallQualifier);
            if (previousDirtyCount < 1 || previousDirtyCount != count(cleanCallOrder, previousCallQualifier)) {
                return null;
            }

            MethodInsnNode nextMethodCall = findFirstInsn(this.after.get(0), MethodInsnNode.class, FORWARD);
            if (nextMethodCall == null) {
                return null;
            }
            String nextCallQualifier = getCallQualifier(nextMethodCall);
            int nextCallIndex = dirtyCallOrder.indexOf(nextCallQualifier);
            if (nextCallIndex == -1) {
                return null;
            }
            int nextDirtyCount = count(dirtyCallOrder, nextCallQualifier);
            if (nextDirtyCount < 1 || nextDirtyCount != count(cleanCallOrder, nextCallQualifier)) {
                return null;
            }

            int diff = nextCallIndex - previousCallIndex;
            if (diff == 2) {
                return dirtyCallOrder.get(previousCallIndex + 1);
            }
            return null;
        }

//        @Nullable
//        public MethodInsnNode findReplacement() {
//            AbstractInsnNode rangeFrom = this.before.get(0);
//            AbstractInsnNode rangeTo = this.after.get(0);
//
//            MethodInsnNode replacement = null;
//            boolean foundLabel = false;
//            for (AbstractInsnNode insn = rangeFrom.getNext(); insn != null; insn = insn.getNext()) {
//                if (insn == rangeTo) {
//                    break;
//                }
//                if (insn instanceof LabelNode) {
//                    if (foundLabel) {
//                        // Multiple labels are not allowed
//                        return null;
//                    } else {
//                        foundLabel = true;
//                    }
//                }
//                if (insn instanceof MethodInsnNode minsn && replacement == null) {
//                    replacement = minsn;
//                }
//            }
//
//            return replacement;
//        }

        public boolean test(InstructionMatcher other) {
            if (this.before.size() == other.before.size() && this.after.size() == other.after.size()) {
                for (int i = 0; i < this.before.size(); i++) {
                    AbstractInsnNode insn = this.before.get(i);
                    AbstractInsnNode otherInsn = other.before.get(i);
                    if (!InsnComparator.instructionsEqual(insn, otherInsn)) {
                        return false;
                    }
                }
                for (int i = 0; i < this.after.size(); i++) {
                    AbstractInsnNode insn = this.after.get(i);
                    AbstractInsnNode otherInsn = other.after.get(i);
                    if (!InsnComparator.instructionsEqual(insn, otherInsn)) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }
}
