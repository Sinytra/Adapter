package org.sinytra.adapter.patch.transformer.dynamic;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.LVTOffsets;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.analysis.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.ParametersDiff;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.LocalVariableLookup;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
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
                Supplier<MethodContext.TargetPair> targetPairSupplier = Suppliers.memoize(methodContext::findDirtyInjectionTarget);
                for (AnnotationNode localAnn : localAnnotations) {
                    result = result.or(offsetVariableIndex(classNode, methodNode, new AnnotationHandle(localAnn), targetPairSupplier));
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
            ParametersDiff diff = compareParameters(classNode, methodNode, methodContext);
            if (diff != null) {
                // Apply parameter patch
                ModifyMethodParams paramTransform = ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.METHOD);
                return paramTransform.apply(classNode, methodNode, methodContext, context);
            }
        }
        return Patch.Result.PASS;
    }

    private Patch.Result offsetVariableIndex(ClassNode classNode, MethodNode methodNode, AnnotationHandle annotation, MethodContext methodContext) {
        return offsetVariableIndex(classNode, methodNode, annotation, methodContext::findDirtyInjectionTarget);
    }

    private Patch.Result offsetVariableIndex(ClassNode classNode, MethodNode methodNode, AnnotationHandle annotation, Supplier<MethodContext.TargetPair> targetPairSupplier) {
        AnnotationValueHandle<Integer> handle = annotation.<Integer>getValue("index").orElse(null);
        if (handle != null) {
            // Find variable index
            int index = handle.get();
            if (index == -1) {
                return Patch.Result.PASS;
            }
            // Get target class and method
            MethodContext.TargetPair targetPair = targetPairSupplier.get();
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
            // Find inserted indexes
            OptionalInt offset = this.lvtOffsets.get().findOffset(targetClass.name, targetMethod.name, targetMethod.desc, index);
            if (offset.isPresent()) {
                int newIndex = index + offset.getAsInt();
                LOGGER.info(PatchInstance.MIXINPATCH, "Offsetting {} index in {}.{} from {} to {}", annotation.getDesc(), classNode.name, methodNode.name, index, newIndex);
                handle.set(newIndex);
                return Patch.Result.APPLY;
            }
        }
        return Patch.Result.PASS;
    }

    @Nullable
    private ParametersDiff compareParameters(ClassNode classNode, MethodNode methodNode, MethodContext methodContext) {
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
        ParametersDiff diff = EnhancedParamsDiff.create(capturedLocals.expected(), availableTypes);
        if (diff.isEmpty()) {
            // No changes required
            return null;
        }
        // Replacements are only partially supported, as most would require LVT fixups and converters
        if (!diff.replacements().isEmpty() && areReplacedParamsUsed(diff.replacements(), methodNode)) {
            // Check if we can rearrange parameters
            ParametersDiff rearrange = ParametersDiff.rearrangeParameters(capturedLocals.expected(), availableTypes);
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
        ParametersDiff offsetDiff = diff.offset(paramLocalStart, maxInsert);
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
}
