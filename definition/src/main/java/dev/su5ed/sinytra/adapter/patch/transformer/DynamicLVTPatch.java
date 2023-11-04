package dev.su5ed.sinytra.adapter.patch.transformer;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.LVTOffsets;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.analysis.ParametersDiff;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.MockMixinRuntime;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.code.ISliceContext;
import org.spongepowered.asm.mixin.injection.code.MethodSlice;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;
import org.spongepowered.asm.mixin.refmap.IMixinContext;
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
        return Set.of(Patch.INJECT, Patch.MODIFY_EXPR_VAL, Patch.MODIFY_VAR);
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
            Supplier<Pair<ClassNode, MethodNode>> targetPairSupplier = Suppliers.memoize(() -> methodContext.findInjectionTarget(annotation, context, AdapterUtil::getClassNode));
            for (Map.Entry<AnnotationNode, Type> entry : localAnnotations.entrySet()) {
                AnnotationNode localAnn = entry.getKey();
                result = result.or(offsetVariableIndex(classNode, methodNode, new AnnotationHandle(localAnn), targetPairSupplier));
            }
            return result;
        }
        if (annotation.matchesDesc(Patch.MODIFY_VAR)) {
            Result result = offsetVariableIndex(classNode, methodNode, annotation, methodContext, context);
            if (result == Result.PASS) {
                AnnotationValueHandle<Integer> ordinal = annotation.<Integer>getValue("ordinal").orElse(null);
                if (ordinal == null && annotation.getValue("name").isEmpty()) {
                    Type[] args = Type.getArgumentTypes(methodNode.desc);
                    if (args.length < 1) {
                        return Result.PASS;
                    }
                    Pair<ClassNode, MethodNode> targetPair = methodContext.findInjectionTarget(annotation, context, AdapterUtil::getClassNode);
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
        if (annotation.matchesDesc(Patch.INJECT) && annotation.getValue("locals").isPresent()) {
            ParametersDiff diff = compareParameters(classNode, methodNode, methodContext, context);
            if (diff != null) {
                // Apply parameter patch
                ModifyMethodParams paramTransform = ModifyMethodParams.create(diff, ModifyMethodParams.TargetType.METHOD);
                return paramTransform.apply(classNode, methodNode, methodContext, context);
            }
        }
        return Result.PASS;
    }

    private Result offsetVariableIndex(ClassNode classNode, MethodNode methodNode, AnnotationHandle annotation, MethodContext methodContext, PatchContext context) {
        return offsetVariableIndex(classNode, methodNode, annotation, () -> methodContext.findInjectionTarget(annotation, context, AdapterUtil::getClassNode));
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
        AnnotationHandle atNode = methodContext.injectionPointAnnotation();
        if (atNode == null) {
            LOGGER.debug("Target @At annotation not found in method {}.{}{}", classNode.name, methodNode.name, methodNode.desc);
            return null;
        }
        AnnotationHandle annotation = methodContext.methodAnnotation();
        // Provide a minimum implementation of IMixinContext
        IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, context.getClassNode().name, context.getEnvironment());
        // Parse injection point
        InjectionPoint injectionPoint = InjectionPoint.parse(mixinContext, methodNode, annotation.unwrap(), atNode.unwrap());
        // Find target instructions
        InsnList instructions = getSlicedInsns(annotation, classNode, methodNode, targetClass, targetMethod, context);
        List<AbstractInsnNode> targetInsns = new ArrayList<>();
        try {
            injectionPoint.find(targetMethod.desc, instructions, targetInsns);
        } catch (InvalidInjectionException | UnsupportedOperationException e) {
            LOGGER.error("Error finding injection insns: {}", e.getMessage());
            return null;
        }
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

    private ParametersDiff compareParameters(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        // Sanity check to make sure the injector method takes in a CI or CIR argument
        if (Stream.of(params).noneMatch(p -> p.equals(CI_TYPE) || p.equals(CIR_TYPE))) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", annotation.getDesc());
            return null;
        }
        Pair<ClassNode, MethodNode> target = methodContext.findInjectionTarget(annotation, context, AdapterUtil::getClassNode);
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
        ParametersDiff diff = ParametersDiff.compareTypeParameters(expected.toArray(Type[]::new), availableTypes.toArray(Type[]::new));
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
        List<Integer> offsetRemovals = diff.removals().stream().filter(i -> i < maxInsert).map(i -> i + paramLocalPos).toList();
        ParametersDiff offsetDiff = new ParametersDiff(diff.originalCount(), offsetInsertions, List.of(), offsetSwaps, offsetRemovals);
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

    private InsnList getSlicedInsns(AnnotationHandle parentAnnotation, ClassNode classNode, MethodNode injectorMethod, ClassNode targetClass, MethodNode targetMethod, PatchContext context) {
        return parentAnnotation.<AnnotationNode>getValue("slice")
            .map(handle -> {
                Object value = handle.get();
                return value instanceof List<?> list ? (AnnotationNode) list.get(0) : (AnnotationNode) value;
            })
            .map(sliceAnn -> {
                IMixinContext mixinContext = MockMixinRuntime.forClass(classNode.name, targetClass.name, context.getEnvironment());
                ISliceContext sliceContext = MockMixinRuntime.forSlice(mixinContext, injectorMethod);
                return computeSlicedInsns(sliceContext, sliceAnn, targetMethod);
            })
            .orElse(targetMethod.instructions);
    }

    private InsnList computeSlicedInsns(ISliceContext context, AnnotationNode annotation, MethodNode method) {
        MethodSlice slice = MethodSlice.parse(context, annotation);
        return slice.getSlice(method);
    }

    private record LocalVariable(int index, Type type) {
    }

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
