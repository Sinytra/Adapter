package dev.su5ed.sinytra.adapter.patch.transformer;

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
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public class DynamicInjectorOrdinalPatch implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

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
        if (annotationHandle == null || annotationHandle.<String>getValue("value").map(h -> !h.get().equals("INVOKE")).orElse(true)) {
            return Patch.Result.PASS;
        }
        String value = annotationHandle.<String>getValue("target").map(AnnotationValueHandle::get).orElse(null);
        if (value == null) {
            return Patch.Result.PASS;
        }

        ClassLookup cleanClassProvider = context.getEnvironment().getCleanClassLookup();
        Pair<ClassNode, MethodNode> cleanTarget = methodContext.findInjectionTarget(classNode, methodContext.methodAnnotation(), context, s -> cleanClassProvider.getClass(s).orElse(null));
        if (cleanTarget == null) {
            return Patch.Result.PASS;
        }

        Pair<ClassNode, MethodNode> dirtyTarget = methodContext.findInjectionTarget(classNode, methodContext.methodAnnotation(), context, AdapterUtil::getClassNode);
        if (dirtyTarget == null) {
            return Patch.Result.PASS;
        }

        Multimap<String, MethodInsnNode> cleanCallsMap = MethodCallAnalyzer.getMethodCalls(cleanTarget.getSecond(), new ArrayList<>());
        Multimap<String, MethodInsnNode> dirtyCallsMap = MethodCallAnalyzer.getMethodCalls(dirtyTarget.getSecond(), new ArrayList<>());

        String cleanValue = context.getEnvironment().remap(classNode.name, value);
        Collection<MethodInsnNode> cleanCalls = cleanCallsMap.get(cleanValue);
        String dirtyValue = context.getEnvironment().remap(classNode.name, value);
        Collection<MethodInsnNode> dirtyCalls = dirtyCallsMap.get(dirtyValue);

        if (cleanCalls.size() != dirtyCalls.size()) {
            int insnRange = 5;
            List<InstructionMatcher> cleanMatchers = cleanCalls.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();
            List<InstructionMatcher> dirtyMatchers = dirtyCalls.stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();

            int ordinalValue = ordinal.get();
            if (ordinalValue >= cleanMatchers.size()) {
                return Patch.Result.PASS;
            }
            InstructionMatcher original = cleanMatchers.get(ordinalValue);
            List<InstructionMatcher> matches = dirtyMatchers.stream()
                .filter(original::test)
                .toList();
            if (matches.size() == 1) {
                int index = dirtyMatchers.indexOf(matches.get(0));
                LOGGER.info(MIXINPATCH, "Updating injection point ordinal of {}.{} from {} to {}", classNode.name, methodNode.name, ordinalValue, index);
                ordinal.set(index);
                return Patch.Result.APPLY;
            }
        }
        return Patch.Result.PASS;
    }
}
