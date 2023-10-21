package dev.su5ed.sinytra.adapter.gradle.analysis;

import com.google.common.collect.Multimap;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.analysis.InstructionMatcher;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ReplacedMethodCalls {
    private static final Logger LOGGER = LoggerFactory.getLogger("ReplacedMethodCalls");

    public static void findReplacedMethodCalls(ClassNode dirtyNode, Map<MethodNode, MethodNode> cleanToDirty, List<Patch> patches, TraceCallback trace) {
        cleanToDirty.forEach((cleanMethod, dirtyMethod) -> {
            int callAnalysisLimit = 2;
            int insnRange = 5;
            List<String> cleanCallOrder = new ArrayList<>();
            List<String> dirtyCallOrder = new ArrayList<>();
            Multimap<String, MethodInsnNode> cleanCalls = MethodCallAnalyzer.getMethodCalls(cleanMethod, cleanCallOrder);
            Multimap<String, MethodInsnNode> dirtyCalls = MethodCallAnalyzer.getMethodCalls(dirtyMethod, dirtyCallOrder);

            dirtyCalls.asMap().forEach((qualifier, dirtyList) -> {
                Collection<MethodInsnNode> cleanList = cleanCalls.get(qualifier);
                if (cleanList.size() != dirtyList.size() && cleanList.size() <= callAnalysisLimit) {
                    List<InstructionMatcher> cleanMatchers = cleanList.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();
                    List<InstructionMatcher> dirtyMatchers = dirtyList.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();

                    List<InstructionMatcher> missing = identifyMissingCalls(cleanMatchers, dirtyMatchers);

                    for (InstructionMatcher matcher : missing) {
                        String original = MethodCallAnalyzer.getCallQualifier(matcher.insn());
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
}
