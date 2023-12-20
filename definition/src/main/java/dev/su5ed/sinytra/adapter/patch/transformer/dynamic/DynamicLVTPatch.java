package dev.su5ed.sinytra.adapter.patch.transformer.dynamic;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.LVTOffsets;
import dev.su5ed.sinytra.adapter.patch.analysis.EnhancedParamsDiff;
import dev.su5ed.sinytra.adapter.patch.analysis.ParametersDiff;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.util.Locals;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record DynamicLVTPatch(Supplier<LVTOffsets> lvtOffsets) implements MethodTransform {
    private static final Type CI_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfo");
    private static final Type CIR_TYPE = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");
    private static final String LOCAL_ANN = "Lcom/llamalad7/mixinextras/sugar/Local;";

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.INJECT, MixinConstants.MODIFY_EXPR_VAL, MixinConstants.MODIFY_VAR);
    }

    @Override
    public Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        if (methodNode.invisibleParameterAnnotations != null) {
            // Find @Local annotations on method parameters
            Type[] paramTypes = Type.getArgumentTypes(methodNode.desc);
            Map<AnnotationNode, Type> localAnnotations = new HashMap<>();
            for (int i = 0; i < methodNode.invisibleParameterAnnotations.length; i++) {
                List<AnnotationNode> parameterAnnotations = methodNode.invisibleParameterAnnotations[i];
                if (parameterAnnotations != null) {
                    for (AnnotationNode paramAnn : parameterAnnotations) {
                        if (LOCAL_ANN.equals(paramAnn.desc)) {
                            Type type = paramTypes[i];
                            localAnnotations.put(paramAnn, type);
                        }
                    }
                }
            }
            if (localAnnotations.isEmpty()) {
                return Result.PASS;
            }
            Result result = Result.PASS;
            Supplier<Pair<ClassNode, MethodNode>> targetPairSupplier = Suppliers.memoize(methodContext::findDirtyInjectionTarget);
            for (Map.Entry<AnnotationNode, Type> entry : localAnnotations.entrySet()) {
                AnnotationNode localAnn = entry.getKey();
                result = result.or(offsetVariableIndex(classNode, methodNode, new AnnotationHandle(localAnn), targetPairSupplier));
            }
            return result;
        }
        if (annotation.matchesDesc(MixinConstants.MODIFY_VAR)) {
            Result result = offsetVariableIndex(classNode, methodNode, annotation, methodContext);
            if (result == Result.PASS) {
                AnnotationValueHandle<Integer> ordinal = annotation.<Integer>getValue("ordinal").orElse(null);
                if (ordinal == null && annotation.getValue("name").isEmpty()) {
                    Type[] args = Type.getArgumentTypes(methodNode.desc);
                    if (args.length < 1) {
                        return Result.PASS;
                    }
                    Pair<ClassNode, MethodNode> targetPair = methodContext.findDirtyInjectionTarget();
                    if (targetPair == null) {
                        return Result.PASS;
                    }
                    List<LocalVariable> available = getTargetMethodLocals(classNode, methodNode, targetPair.getFirst(), targetPair.getSecond(), methodContext, context, 0, FabricUtil.COMPATIBILITY_0_9_2);
                    if (available == null) {
                        return Result.PASS;
                    }
                    Type expected = args[0];
                    int count = (int) available.stream().filter(lv -> lv.type.equals(expected)).count();
                    if (count == 1) {
                        annotation.appendValue("ordinal", 0);
                        return Result.APPLY;
                    }
                }
            }
            return result;
        }
        // Check if the mixin captures LVT
        if (annotation.matchesDesc(MixinConstants.INJECT) && annotation.getValue("locals").isPresent()) {
            ParametersDiff diff = compareParameters(classNode, methodNode, methodContext, context);
            if (diff != null) {
                // Apply parameter patch
                ModifyMethodParams paramTransform = ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.METHOD);
                return paramTransform.apply(classNode, methodNode, methodContext, context);
            }
        }
        return Result.PASS;
    }

    private Result offsetVariableIndex(ClassNode classNode, MethodNode methodNode, AnnotationHandle annotation, MethodContext methodContext) {
        return offsetVariableIndex(classNode, methodNode, annotation, methodContext::findDirtyInjectionTarget);
    }

    private Result offsetVariableIndex(ClassNode classNode, MethodNode methodNode, AnnotationHandle annotation, Supplier<Pair<ClassNode, MethodNode>> targetPairSupplier) {
        AnnotationValueHandle<Integer> handle = annotation.<Integer>getValue("index").orElse(null);
        if (handle != null) {
            // Find variable index
            int index = handle.get();
            if (index == -1) {
                return Result.PASS;
            }
            // Get target class and method
            Pair<ClassNode, MethodNode> targetPair = targetPairSupplier.get();
            if (targetPair == null) {
                return Result.PASS;
            }
            ClassNode targetClass = targetPair.getFirst();
            MethodNode targetMethod = targetPair.getSecond();
            // Find reordered indices
            OptionalInt reorder = this.lvtOffsets.get().findReorder(targetClass.name, targetMethod.name, targetMethod.desc, index);
            if (reorder.isPresent()) {
                int newIndex = reorder.getAsInt();
                LOGGER.info(MIXINPATCH, "Swapping {} index in {}.{} from {} for {}", annotation.getDesc(), classNode.name, methodNode.name, index, newIndex);
                handle.set(newIndex);
                return Result.APPLY;
            }
            // Find inserted indexes
            OptionalInt offset = this.lvtOffsets.get().findOffset(targetClass.name, targetMethod.name, targetMethod.desc, index);
            if (offset.isPresent()) {
                int newIndex = index + offset.getAsInt();
                LOGGER.info(MIXINPATCH, "Offsetting {} index in {}.{} from {} to {}", annotation.getDesc(), classNode.name, methodNode.name, index, newIndex);
                handle.set(newIndex);
                return Result.APPLY;
            }
        }
        return Result.PASS;
    }

    @Nullable
    private List<LocalVariable> getTargetMethodLocals(ClassNode classNode, MethodNode methodNode, ClassNode targetClass, MethodNode targetMethod, MethodContext methodContext, PatchContext context, int startPos, int fabricCompatibility) {
        List<AbstractInsnNode> targetInsns = methodContext.findInjectionTargetInsns(classNode, targetClass, methodNode, targetMethod, context);
        if (targetInsns.isEmpty()) {
            LOGGER.debug("Skipping LVT patch, no target instructions found");
            return null;
        }
        // Get available local variables at the injection point in the target method
        LocalVariableNode[] localVariables;
        // Synchronize to avoid issues in mixin. This is necessary.
        synchronized (this) {
            localVariables = Locals.getLocalsAt(targetClass, targetMethod, targetInsns.get(0), fabricCompatibility);
        }
        LocalVariable[] locals = Stream.of(localVariables)
            .filter(Objects::nonNull)
            .map(lv -> new LocalVariable(lv.index, Type.getType(lv.desc)))
            .toArray(LocalVariable[]::new);
        return summariseLocals(locals, startPos);
    }

    @Nullable
    private ParametersDiff compareParameters(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        // Sanity check to make sure the injector method takes in a CI or CIR argument
        if (Stream.of(params).noneMatch(p -> p.equals(CI_TYPE) || p.equals(CIR_TYPE))) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", annotation.getDesc());
            return null;
        }
        Pair<ClassNode, MethodNode> target = methodContext.findDirtyInjectionTarget();
        if (target == null) {
            return null;
        }
        ClassNode targetClass = target.getFirst();
        MethodNode targetMethod = target.getSecond();
        Type[] targetParams = Type.getArgumentTypes(targetMethod.desc);
        boolean isStatic = (methodNode.access & Opcodes.ACC_STATIC) != 0;
        int lvtOffset = isStatic ? 0 : 1;
        // The starting LVT index is of the first var after all method parameters. Offset by 1 for instance methods to skip 'this'
        int targetLocalPos = targetParams.length + lvtOffset;
        // The first local var in the method's params comes after the target's params plus the CI/CIR parameter
        int paramLocalPos = targetParams.length + 1;
        // Get expected local variables from method parameters
        List<Type> expected = summariseLocals(params, paramLocalPos);
        // Get available local variables at the injection point in the target method
        List<LocalVariable> available = getTargetMethodLocals(classNode, methodNode, targetClass, targetMethod, methodContext, context, targetLocalPos, FabricUtil.COMPATIBILITY_LATEST);
        if (available == null) {
            return null;
        }
        List<Type> availableTypes = available.stream().map(LocalVariable::type).toList();
        // Compare expected and available params
        ParametersDiff diff = EnhancedParamsDiff.create(expected, availableTypes);
        if (diff.isEmpty()) {
            // No changes required
            return null;
        }
        // Replacements are not supported, as they would require LVT fixups and converters
        if (!diff.replacements().isEmpty()) {
            // Check if we can rearrange parameters
            ParametersDiff rearrange = ParametersDiff.rearrangeParameters(expected, availableTypes);
            if (rearrange == null) {
                LOGGER.debug("Tried to replace local variables in mixin method {}.{} using {}", classNode.name, methodNode.name + methodNode.desc, diff.replacements());
                return null;
            }
            diff = rearrange;
        }
        if (!diff.removals().isEmpty()) {
            List<LocalVariableNode> lvt = methodNode.localVariables.stream().sorted(Comparator.comparingInt(lvn -> lvn.index)).toList();
            for (int removal : diff.removals()) {
                int removalLocal = removal + lvtOffset + paramLocalPos;
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
        int maxInsert = getMaxLocalIndex(expected, diff.insertions());
        List<Pair<Integer, Type>> offsetInsertions = diff.insertions().stream().filter(pair -> pair.getFirst() < maxInsert).map(pair -> pair.mapFirst(i -> i + paramLocalPos)).toList();
        List<Pair<Integer, Integer>> offsetSwaps = diff.swaps().stream().filter(pair -> pair.getFirst() < maxInsert).map(pair -> pair.mapFirst(i -> i + paramLocalPos).mapSecond(i -> i + paramLocalPos)).toList();
        List<Pair<Integer, Integer>> offsetMoves = diff.moves().stream().filter(pair -> pair.getFirst() < maxInsert).map(pair -> pair.mapFirst(i -> i + paramLocalPos).mapSecond(i -> i + paramLocalPos)).toList();
        List<Integer> offsetRemovals = diff.removals().stream().filter(i -> i < maxInsert).map(i -> i + paramLocalPos).toList();
        ParametersDiff offsetDiff = new ParametersDiff(diff.originalCount(), offsetInsertions, List.of(), offsetSwaps, offsetRemovals, offsetMoves);
        if (offsetDiff.isEmpty()) {
            // No changes required
            return null;
        }
        return offsetDiff;
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

    private record LocalVariable(int index, Type type) {}

    // Adapted from org.spongepowered.asm.mixin.injection.callback.CallbackInjector summariseLocals
    private static <T> List<T> summariseLocals(T[] locals, int pos) {
        List<T> list = new ArrayList<>();
        if (locals != null) {
            for (int i = pos; i < locals.length; i++) {
                if (locals[i] != null) {
                    list.add(locals[i]);
                }
            }
        }
        return list;
    }
}
