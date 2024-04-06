package org.sinytra.adapter.patch.transformer.dynamic;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.LVTOffsets;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.analysis.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.ParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.params.SimpleParamsDiffSnapshot;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public record DynamicLVTPatch(Supplier<LVTOffsets> lvtOffsets) implements MethodTransform {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> ANNOTATIONS = Set.of(MixinConstants.INJECT, MixinConstants.MODIFY_EXPR_VAL, MixinConstants.MODIFY_VAR);

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        if (methodNode.invisibleParameterAnnotations != null) {
            // Find @Local annotations on method parameters
            Type[] paramTypes = Type.getArgumentTypes(methodNode.desc);
            List<AnnotationNode> localAnnotations = AdapterUtil.getAnnotatedParameters(methodNode, paramTypes, MixinConstants.LOCAL, (node, type) -> node);
            if (!localAnnotations.isEmpty()) {
                Patch.Result result = Patch.Result.PASS;
                for (AnnotationNode localAnn : localAnnotations) {
                    result = result.or(offsetVariableIndex(classNode, methodNode, new AnnotationHandle(localAnn), methodContext));
                }
                return result;
            }
        }
        if (!ANNOTATIONS.contains(annotation.getDesc())) {
            return Patch.Result.PASS;
        }
        if (annotation.matchesDesc(MixinConstants.MODIFY_VAR)) {
            Patch.Result result = offsetVariableIndex(classNode, methodNode, annotation, methodContext);
            if (result == Patch.Result.PASS) {
                AnnotationValueHandle<Integer> ordinal = annotation.<Integer>getValue("ordinal").orElse(null);
                if (ordinal == null && annotation.getValue("name").isEmpty()) {
                    Type[] args = Type.getArgumentTypes(methodNode.desc);
                    if (args.length < 1) {
                        return Patch.Result.PASS;
                    }
                    MethodContext.TargetPair targetPair = methodContext.findDirtyInjectionTarget();
                    if (targetPair == null) {
                        return Patch.Result.PASS;
                    }
                    for (Integer level : methodContext.getLvtCompatLevelsOrdered()) {
                        List<MethodContext.LocalVariable> available = methodContext.getTargetMethodLocals(targetPair, 0, level);
                        if (available == null) {
                            return Patch.Result.PASS;
                        }
                        Type expected = args[0];
                        int count = (int) available.stream().filter(lv -> lv.type().equals(expected)).count();
                        if (count == 1) {
                            annotation.appendValue("ordinal", 0);
                            return Patch.Result.APPLY;
                        }
                    }
                }
            }
            return result;
        }
        // Check if the mixin captures LVT
        if (annotation.matchesDesc(MixinConstants.INJECT) && annotation.getValue("locals").isPresent()) {
            ParamsDiffSnapshot diff = compareParameters(classNode, methodNode, methodContext);
            if (diff != null) {
                // Apply parameter patch
                MethodTransform transform = diff.asParameterTransformer(ParamTransformTarget.METHOD, true);
                return transform.apply(classNode, methodNode, methodContext, methodContext.patchContext());
            }
        }
        return Patch.Result.PASS;
    }

    private Patch.Result offsetVariableIndex(ClassNode classNode, MethodNode methodNode, AnnotationHandle annotation, MethodContext methodContext) {
        AnnotationValueHandle<Integer> handle = annotation.<Integer>getValue("index").orElse(null);
        if (handle != null) {
            // Find variable index
            int index = handle.get();
            if (index == -1) {
                return Patch.Result.PASS;
            }
            // Get target class and method
            MethodContext.TargetPair targetPair = methodContext.findDirtyInjectionTarget();
            if (targetPair == null) {
                return Patch.Result.PASS;
            }
            ClassNode targetClass = targetPair.classNode();
            MethodNode targetMethod = targetPair.methodNode();
            // Find reordered indices
            OptionalInt reorder = this.lvtOffsets.get().findReorder(targetClass.name, targetMethod.name, targetMethod.desc, index);
            if (reorder.isPresent()) {
                int newIndex = reorder.getAsInt();
                LOGGER.info(PatchInstance.MIXINPATCH, "Swapping {} index in {}.{} from {} for {}", annotation.getDesc(), classNode.name, methodNode.name, index, newIndex);
                handle.set(newIndex);
                return Patch.Result.APPLY;
            }
        }
        return Patch.Result.PASS;
    }

    @Nullable
    private ParamsDiffSnapshot compareParameters(ClassNode classNode, MethodNode methodNode, MethodContext methodContext) {
        AdapterUtil.CapturedLocals capturedLocals = AdapterUtil.getCapturedLocals(methodNode, methodContext);
        if (capturedLocals == null) {
            return null;
        }

        // Get available local variables at the injection point in the target method
        List<MethodContext.LocalVariable> available = methodContext.getTargetMethodLocals(capturedLocals.target());
        if (available == null) {
            return null;
        }
        List<Type> availableTypes = available.stream().map(MethodContext.LocalVariable::type).toList();
        // Compare expected and available params
        ParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(capturedLocals.expected(), availableTypes);
        if (diff.isEmpty()) {
            // No changes required
            return null;
        }
        // Replacements are only partially supported, as most would require LVT fixups and converters
        if (!diff.replacements().isEmpty() && areReplacedParamsUsed(diff.replacements(), methodNode)) {
            // Check if we can rearrange parameters
            SimpleParamsDiffSnapshot rearrange = rearrangeParameters(capturedLocals.expected(), availableTypes);
            if (rearrange == null) {
                LOGGER.debug("Tried to replace local variables in mixin method {}.{} using {}", classNode.name, methodNode.name + methodNode.desc, diff.replacements());
                return null;
            }
            diff = rearrange;
        }

        int paramLocalStart = capturedLocals.paramLocalStart();
        if (!diff.removals().isEmpty()) {
            List<LocalVariableNode> lvt = methodNode.localVariables.stream().sorted(Comparator.comparingInt(lvn -> lvn.index)).toList();
            for (int removal : diff.removals()) {
                int removalLocal = removal + capturedLocals.lvtOffset() + paramLocalStart;
                if (removalLocal >= lvt.size()) {
                    continue;
                }
                int removalIndex = lvt.get(removalLocal).index;
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (insn instanceof VarInsnNode varInsn && varInsn.var == removalIndex) {
                        LOGGER.debug("Cannot remove parameter {} in mixin method {}.{}", removal, classNode.name, methodNode.name + methodNode.desc);
                        return null;
                    }
                }
            }
        }
        // Offset the insertion to the correct parameter indices
        // Also remove any appended variables
        int maxInsert = getMaxLocalIndex(capturedLocals.expected(), diff.insertions());
        ParamsDiffSnapshot offsetDiff = diff.offset(paramLocalStart, maxInsert);
        if (offsetDiff.isEmpty()) {
            // No changes required
            return null;
        }
        return offsetDiff;
    }

    private static boolean areReplacedParamsUsed(List<Pair<Integer, Type>> replacements, MethodNode methodNode) {
        LocalVariableLookup lookup = new LocalVariableLookup(methodNode);
        Set<Integer> paramLocals = replacements.stream()
            .map(p -> lookup.getByParameterOrdinal(p.getFirst()).index)
            .collect(Collectors.toSet());
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && paramLocals.contains(varInsn.var)) {
                return true;
            }
        }
        return false;
    }

    private static int getMaxLocalIndex(List<Type> expected, List<Pair<Integer, Type>> insertions) {
        int maxIndex = expected.size();
        for (Pair<Integer, Type> pair : insertions) {
            int at = pair.getFirst();
            if (at < maxIndex) {
                maxIndex++;
            }
        }
        return maxIndex;
    }

    // TODO Replace by LocalVarRearrangement#getRearrangedParameters ?
    @VisibleForTesting
    @Nullable
    public static SimpleParamsDiffSnapshot rearrangeParameters(List<Type> parameterTypes, List<Type> newParameterTypes) {
        Object2IntMap<Type> typeCount = new Object2IntOpenHashMap<>();
        ListMultimap<Type, Integer> typeIndices = ArrayListMultimap.create();
        for (int i = 0; i < parameterTypes.size(); i++) {
            Type type = parameterTypes.get(i);
            typeCount.put(type, typeCount.getInt(type) + 1);
            typeIndices.put(type, i);
        }
        Object2IntMap<Type> newTypeCount = new Object2IntOpenHashMap<>();
        for (Type type : newParameterTypes) {
            newTypeCount.put(type, newTypeCount.getInt(type) + 1);
        }

        for (Object2IntMap.Entry<Type> entry : typeCount.object2IntEntrySet()) {
            if (newTypeCount.getInt(entry.getKey()) != entry.getIntValue()) {
                return null;
            }
        }

        List<Pair<Integer, Type>> insertions = new ArrayList<>();
        for (int i = 0; i < newParameterTypes.size(); i++) {
            Type type = newParameterTypes.get(i);
            if (!typeCount.containsKey(type)) {
                insertions.add(Pair.of(i, type));
            }
        }

        Object2IntMap<Type> seenTypes = new Object2IntOpenHashMap<>();
        Int2IntMap swaps = new Int2IntLinkedOpenHashMap();
        for (int i = 0; i < newParameterTypes.size(); i++) {
            Type type = newParameterTypes.get(i);
            if (typeIndices.containsKey(type)) {
                List<Integer> indices = typeIndices.get(type);
                int seen = seenTypes.getInt(type);
                int oldIndex = indices.get(seen);
                seenTypes.put(type, seen + 1);
                if (oldIndex != i && !swaps.containsKey(i)) {
                    swaps.put(oldIndex, i);
                }
            }
        }

        if (swaps.isEmpty()) {
            return null;
        }

        List<Pair<Integer, Integer>> swapsList = new ArrayList<>();
        swaps.forEach((from, to) -> swapsList.add(Pair.of(from, to)));

        return SimpleParamsDiffSnapshot.builder()
            .insertions(insertions)
            .swaps(swapsList)
            .build();
    }
}
