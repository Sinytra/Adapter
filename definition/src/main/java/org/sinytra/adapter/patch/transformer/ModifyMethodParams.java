package org.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.PatchInstance;
import org.sinytra.adapter.patch.analysis.LocalVariableLookup;
import org.sinytra.adapter.patch.analysis.params.SimpleParamsDiffSnapshot;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.selector.AnnotationHandle;
import org.sinytra.adapter.patch.transformer.param.InjectParameterTransform;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;
import org.sinytra.adapter.patch.transformer.param.SwapParametersTransformer;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.SingleValueHandle;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;

import static org.sinytra.adapter.patch.PatchInstance.MIXINPATCH;
import static org.sinytra.adapter.patch.transformer.param.ParamTransformationUtil.calculateLVTIndex;
import static org.sinytra.adapter.patch.transformer.param.ParamTransformationUtil.findWrapOperationOriginalCall;

// TODO Refactor
public record ModifyMethodParams(SimpleParamsDiffSnapshot context, ParamTransformTarget targetType, boolean ignoreOffset, @Nullable LVTFixer lvtFixer) implements MethodTransform {
    public static final Codec<ModifyMethodParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        SimpleParamsDiffSnapshot.CODEC.fieldOf("context").forGetter(ModifyMethodParams::context),
        ParamTransformTarget.CODEC.optionalFieldOf("targetInjectionPoint", ParamTransformTarget.ALL).forGetter(ModifyMethodParams::targetType)
    ).apply(instance, (context, targetInjectionPoint) -> new ModifyMethodParams(context, targetInjectionPoint, false, null)));

    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static ModifyMethodParams create(SimpleParamsDiffSnapshot diff, ParamTransformTarget targetType) {
        return new ModifyMethodParams(diff, targetType, false, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModifyMethodParams {
        if (context.isEmpty()) {
            throw new IllegalArgumentException("Method parameter transformation contains no changes");
        }
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Collection<String> getAcceptedAnnotations() {
        Collection<String> targets = this.targetType.getTargetMixinTypes();
        return targets.isEmpty() ? PatchInstance.KNOWN_MIXIN_TYPES : targets;
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        AnnotationHandle annotation = methodContext.methodAnnotation();
        Type[] params = Type.getArgumentTypes(methodNode.desc);
        List<Type> newParameterTypes = new ArrayList<>(Arrays.asList(params));
        boolean isNonStatic = (methodNode.access & Opcodes.ACC_STATIC) == 0;
        boolean needsOffset = annotation.matchesDesc(MixinConstants.REDIRECT) && !this.ignoreOffset;
        int offset = isNonStatic
            // If it's a redirect, the first local variable (index 1) is the object instance
            ? needsOffset ? 2 : 1
            : 0;

        if (annotation.matchesDesc(MixinConstants.MODIFY_VAR)) {
            annotation.<Integer>getValue("index").ifPresent(indexHandle -> this.context.insertions().forEach(pair -> {
                int localIndex = offset + pair.getFirst();
                int indexValue = indexHandle.get();
                if (indexValue >= localIndex) {
                    indexHandle.set(indexValue + 1);
                }
            }));
            return Patch.Result.APPLY;
        }
        if (annotation.matchesDesc(MixinConstants.MODIFY_ARGS)) {
            ModifyArgsOffsetTransformer.modify(methodNode, this.context.insertions());
            return Patch.Result.APPLY;
        }

        List<Pair<Integer, Integer>> offsetSwaps = new ArrayList<>(this.context.swaps());
        List<Pair<Integer, Integer>> offsetMoves = new ArrayList<>(this.context.moves());
        LocalVariableNode self = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();
        Deque<Pair<Integer, Type>> insertionQueue = new ArrayDeque<>(this.context.insertions());
        while (!insertionQueue.isEmpty()) {
            Pair<Integer, Type> pair = insertionQueue.pop();
            int index = pair.getFirst();
            Type type = pair.getSecond();
            if (index > newParameterTypes.size() + 1) {
                continue;
            }

            final LVTSnapshot snapshot = LVTSnapshot.take(methodNode);

            int lvtIndex = calculateLVTIndex(newParameterTypes, isNonStatic, (needsOffset ? 1 : 0) + index);
            int paramOrdinal = isNonStatic && needsOffset ? index + 1 : index;
            ParameterNode newParameter = new ParameterNode("adapter_injected_" + paramOrdinal, Opcodes.ACC_SYNTHETIC);
            newParameterTypes.add(paramOrdinal, type);
            methodNode.parameters.add(paramOrdinal, newParameter);

            InjectParameterTransform.offsetParameters(methodNode, paramOrdinal);

            offsetSwaps.replaceAll(integerIntegerPair -> integerIntegerPair.mapFirst(j -> j >= paramOrdinal ? j + 1 : j));
            offsetMoves.replaceAll(integerIntegerPair -> integerIntegerPair.mapFirst(j -> j >= paramOrdinal ? j + 1 : j).mapSecond(j -> j >= paramOrdinal ? j + 1 : j));

            methodNode.localVariables.add(paramOrdinal + (isNonStatic ? 1 : 0), new LocalVariableNode(newParameter.name, type.getDescriptor(), null, self.start, self.end, lvtIndex));
            snapshot.applyDifference(methodNode);
        }
        LocalVariableLookup lvtLookup = new LocalVariableLookup(methodNode);
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        this.context.replacements().forEach(pair -> {
            int index = pair.getFirst();
            Type type = pair.getSecond();
            newParameterTypes.set(index, type);
            int lvtOrdinal = offset + index;
            LocalVariableNode localVar = lvtLookup.getByOrdinal(lvtOrdinal);
            int localIndex = localVar.index;
            Type originalType = Type.getType(localVar.desc);
            localVar.desc = type.getDescriptor();
            localVar.signature = null;
            List<AbstractInsnNode> ignoreInsns = findWrapOperationOriginalCall(methodNode, methodContext);
            if (type.getSort() == Type.OBJECT && originalType.getSort() == Type.OBJECT) {
                // Replace variable usages with the new type
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (ignoreInsns.contains(insn)) {
                        continue;
                    }
                    if (insn instanceof MethodInsnNode minsn && minsn.owner.equals(originalType.getInternalName())) {
                        // Find var load instruction
                        AbstractInsnNode previous = minsn.getPrevious();
                        if (previous != null) {
                            do {
                                // Limit scope to the current label / line only
                                if (previous instanceof LabelNode || previous instanceof LineNumberNode) {
                                    break;
                                }
                                if (previous instanceof VarInsnNode varinsn && varinsn.var == localIndex) {
                                    minsn.owner = type.getInternalName();
                                    break;
                                }
                            } while ((previous = previous.getPrevious()) != null);
                        }
                    }
                    if (insn instanceof VarInsnNode varInsn && varInsn.var == localIndex) {
                        int nextOp = insn.getNext().getOpcode();
                        if (bfu != null && nextOp != Opcodes.IFNULL && nextOp != Opcodes.IFNONNULL) {
                            TypeAdapter typeFix = bfu.getTypeAdapter(type, originalType);
                            if (typeFix != null) {
                                typeFix.apply(methodNode.instructions, varInsn);
                            }
                        }
                        if (this.lvtFixer != null) {
                            this.lvtFixer.accept(varInsn.var, varInsn, methodNode.instructions);
                        }
                    }
                }
            }
        });
        this.context.substitutes().forEach(pair -> {
            int paramIndex = pair.getFirst();
            int substituteParamIndex = pair.getSecond();
            if (methodNode.parameters.size() > paramIndex) {
                int localIndex = calculateLVTIndex(newParameterTypes, isNonStatic, paramIndex);
                LVTSnapshot lvtSnapshot = LVTSnapshot.take(methodNode);
                LOGGER.info("Substituting parameter {} for {} in {}.{}", paramIndex, substituteParamIndex, classNode.name, methodNode.name);
                methodNode.parameters.remove(paramIndex);
                newParameterTypes.remove(paramIndex);
                int substituteIndex = calculateLVTIndex(newParameterTypes, isNonStatic, substituteParamIndex);
                methodNode.localVariables.removeIf(lvn -> lvn.index == localIndex);
                for (AbstractInsnNode insn : methodNode.instructions) {
                    SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                    if (handle == null) continue;

                    if (handle.get() == localIndex) {
                        handle.set(substituteIndex);
                    }
                }
                lvtSnapshot.applyDifference(methodNode);
            }
        });
        for (Pair<Integer, Integer> swapPair : offsetSwaps) {
            int from = swapPair.getFirst();
            int to = swapPair.getSecond();
            ParameterNode fromNode = methodNode.parameters.get(from);
            ParameterNode toNode = methodNode.parameters.get(to);

            int fromOldLVT = calculateLVTIndex(newParameterTypes, isNonStatic, from);
            int toOldLVT = calculateLVTIndex(newParameterTypes, isNonStatic, to);

            methodNode.parameters.set(from, toNode);
            methodNode.parameters.set(to, fromNode);
            Type fromType = newParameterTypes.get(from);
            Type toType = newParameterTypes.get(to);
            newParameterTypes.set(from, toType);
            newParameterTypes.set(to, fromType);
            LOGGER.info(MIXINPATCH, "Swapped parameters at positions {}({}) and {}({}) in {}.{}", from, fromNode.name, to, toNode.name, classNode.name, methodNode.name);

            int fromNewLVT = calculateLVTIndex(newParameterTypes, isNonStatic, from);
            int toNewLVT = calculateLVTIndex(newParameterTypes, isNonStatic, to);

            // Account for "big" LVT variables (like longs and doubles)
            // Uses of the old parameter need to be the new parameter and vice versa
            SwapParametersTransformer.swapLVT(methodNode, fromOldLVT, toNewLVT)
                .andThen(SwapParametersTransformer.swapLVT(methodNode, toOldLVT, fromNewLVT))
                .accept(null);
        }

        if (!this.context.removals().isEmpty()) {
            LOGGER.info(MIXINPATCH, "Removing parameters {} from method {}.{}", this.context.removals(), classNode.name, methodNode.name);
        }
        this.context.removals().stream()
            .sorted(Comparator.<Integer>comparingInt(i -> i).reversed())
            .forEach(removal -> removeLocalVariable(methodNode, removal, offset, -1, newParameterTypes));
        offsetMoves.forEach(move -> {
            int from = move.getFirst();
            int to = move.getSecond();
            LOGGER.info(MIXINPATCH, "Moving parameter from index {} to {} in method {}.{}", from, to, classNode.name, methodNode.name);
            int tempIndex = -999;
            Pair<@Nullable ParameterNode, @Nullable LocalVariableNode> removed = removeLocalVariable(methodNode, from, offset, tempIndex, newParameterTypes);
            if (removed.getFirst() != null) {
                methodNode.parameters.add(to, removed.getFirst());
            }
            LocalVariableNode lvn = removed.getSecond();
            if (lvn != null) {
                Type type = Type.getType(lvn.desc);
                int varOffset = AdapterUtil.getLVTOffsetForType(type);
                if (to > from) {
                    to -= varOffset;
                }
                lvn.index = to + offset;
                offsetLVT(methodNode, lvn.index, varOffset);
                methodNode.localVariables.add(lvn.index, lvn);
                for (AbstractInsnNode insn : methodNode.instructions) {
                    SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                    if (handle != null && handle.get() == tempIndex) {
                        handle.set(lvn.index);
                    }
                }
                newParameterTypes.add(to, type);
            }
        });
        this.context.inlines().stream()
            .sorted(Comparator.<Pair<Integer, Consumer<InstructionAdapter>>>comparingInt(Pair::getFirst).reversed())
            .forEach(inline -> {
                int index = inline.getFirst();
                LOGGER.info(MIXINPATCH, "Inlining parameter {} of method {}.{}", index, classNode.name, methodNode.name);
                int replaceIndex = -999 + index;
                removeLocalVariable(methodNode, index, offset, replaceIndex, newParameterTypes);
                for (AbstractInsnNode insn : methodNode.instructions) {
                    if (insn instanceof VarInsnNode varInsn && varInsn.var == replaceIndex) {
                        InsnList replacementInsns = AdapterUtil.insnsWithAdapter(inline.getSecond());
                        methodNode.instructions.insert(varInsn, replacementInsns);
                        methodNode.instructions.remove(varInsn);
                    }
                }
            });

        methodContext.updateDescription(newParameterTypes);

        return this.context.shouldComputeFrames() ? Patch.Result.COMPUTE_FRAMES : Patch.Result.APPLY;
    }

    private static Pair<@Nullable ParameterNode, @Nullable LocalVariableNode> removeLocalVariable(MethodNode methodNode, int paramIndex, int lvtOffset, int replaceIndex, List<Type> newParameterTypes) {
        final LVTSnapshot snapshot = LVTSnapshot.take(methodNode);
        ParameterNode parameter = paramIndex < methodNode.parameters.size() ? methodNode.parameters.remove(paramIndex) : null;
        methodNode.localVariables.sort(Comparator.comparingInt(lvn -> lvn.index));
        LocalVariableNode lvn = methodNode.localVariables.remove(paramIndex + lvtOffset);
        if (lvn != null) {
            for (AbstractInsnNode insn : methodNode.instructions) {
                SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                if (handle != null) {
                    if (handle.get() == lvn.index) {
                        handle.set(replaceIndex);
                    }
                }
            }
        }
        newParameterTypes.remove(paramIndex);
        snapshot.applyDifference(methodNode);
        return Pair.of(parameter, lvn);
    }

    private static void offsetLVT(MethodNode methodNode, int lvtIndex, int offset) {
        for (LocalVariableNode localVariable : methodNode.localVariables) {
            if (localVariable.index >= lvtIndex) {
                localVariable.index += offset;
            }
        }

        for (AbstractInsnNode insn : methodNode.instructions) {
            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
            if (handle != null && handle.get() >= lvtIndex) {
                handle.set(handle.get() + offset);
            }
        }

        // TODO All visible/invisible annotations
        if (methodNode.visibleLocalVariableAnnotations != null) {
            for (LocalVariableAnnotationNode localVariableAnnotation : methodNode.visibleLocalVariableAnnotations) {
                List<Integer> annotationIndices = localVariableAnnotation.index;
                for (int j = 0; j < annotationIndices.size(); j++) {
                    Integer annoIndex = annotationIndices.get(j);
                    if (annoIndex >= lvtIndex) {
                        annotationIndices.set(j, annoIndex + 1);
                    }
                }
            }
        }
    }

    public interface LVTFixer {
        void accept(int index, AbstractInsnNode insn, InsnList list);
    }

    public static class Builder {
        private final List<Pair<Integer, Type>> insertions = new ArrayList<>();
        private final List<Pair<Integer, Type>> replacements = new ArrayList<>();
        private final List<Pair<Integer, Integer>> substitutes = new ArrayList<>();
        private final List<Integer> removals = new ArrayList<>();
        private final List<Pair<Integer, Integer>> swap = new ArrayList<>();
        private final List<Pair<Integer, Consumer<InstructionAdapter>>> inlines = new ArrayList<>();
        private ParamTransformTarget targetType = ParamTransformTarget.ALL;
        private boolean ignoreOffset = false;
        @Nullable
        private LVTFixer lvtFixer;

        public Builder insert(int index, Type type) {
            this.insertions.add(Pair.of(index, type));
            return this;
        }

        public Builder inline(int index, Consumer<InstructionAdapter> inliner) {
            this.inlines.add(Pair.of(index, inliner));
            return this;
        }

        public Builder replacements(List<Pair<Integer, Type>> replacements) {
            this.replacements.addAll(replacements);
            return this;
        }

        public Builder replace(int index, Type type) {
            this.replacements.add(Pair.of(index, type));
            return this;
        }

        public Builder substitute(int index, int substitute) {
            this.substitutes.add(Pair.of(index, substitute));
            return this;
        }

        public Builder remove(int index) {
            this.removals.add(index);
            return this;
        }

        public Builder remove(Collection<Integer> indices) {
            this.removals.addAll(indices);
            return this;
        }

        public Builder swap(int original, int replacement) {
            this.swap.add(Pair.of(original, replacement));
            return this;
        }

        public Builder targetType(ParamTransformTarget targetType) {
            this.targetType = targetType;
            return this;
        }

        public Builder ignoreOffset() {
            this.ignoreOffset = true;
            return this;
        }

        public Builder lvtFixer(LVTFixer lvtFixer) {
            this.lvtFixer = lvtFixer;
            return this;
        }
        
        public Builder chain(Consumer<Builder> consumer) {
            consumer.accept(this);
            return this;
        }

        public ModifyMethodParams build() {
            SimpleParamsDiffSnapshot context = new SimpleParamsDiffSnapshot(this.insertions, this.replacements, this.swap, this.substitutes, this.removals, List.of(), this.inlines);
            return new ModifyMethodParams(context, this.targetType, this.ignoreOffset, this.lvtFixer);
        }
    }
}
