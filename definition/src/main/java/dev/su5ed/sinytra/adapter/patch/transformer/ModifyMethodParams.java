package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import dev.su5ed.sinytra.adapter.patch.analysis.ParametersDiff;
import dev.su5ed.sinytra.adapter.patch.api.MethodContext;
import dev.su5ed.sinytra.adapter.patch.api.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.api.MixinConstants;
import dev.su5ed.sinytra.adapter.patch.api.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import dev.su5ed.sinytra.adapter.patch.fixes.TypeAdapter;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.LocalVariableLookup;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import dev.su5ed.sinytra.adapter.patch.util.SingleValueHandle;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

// TODO Refactor
public record ModifyMethodParams(ParamsContext context, TargetType targetType, boolean ignoreOffset, @Nullable LVTFixer lvtFixer) implements MethodTransform {
    public static final Codec<ModifyMethodParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ParamsContext.CODEC.fieldOf("context").forGetter(ModifyMethodParams::context),
        TargetType.CODEC.optionalFieldOf("targetInjectionPoint", TargetType.ALL).forGetter(ModifyMethodParams::targetType)
    ).apply(instance, (context, targetInjectionPoint) -> new ModifyMethodParams(context, targetInjectionPoint, false, null)));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final MethodQualifier WO_ORIGINAL_CALL = new MethodQualifier("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;", "call", "([Ljava/lang/Object;)Ljava/lang/Object;");

    public static ModifyMethodParams create(String cleanMethodDesc, String dirtyMethodDesc, TargetType targetType) {
        ParametersDiff diff = ParametersDiff.compareTypeParameters(Type.getArgumentTypes(cleanMethodDesc), Type.getArgumentTypes(dirtyMethodDesc));
        return create(diff, targetType);
    }

    public static ModifyMethodParams create(ParametersDiff diff, TargetType targetType) {
        return new ModifyMethodParams(ParamsContext.create(diff), targetType, false, null);
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
    public Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
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
            annotation.<Integer>getValue("index").ifPresent(indexHandle -> this.context.insertions.forEach(pair -> {
                int localIndex = offset + pair.getFirst();
                int indexValue = indexHandle.get();
                if (indexValue >= localIndex) {
                    indexHandle.set(indexValue + 1);
                }
            }));
            return Result.APPLY;
        }
        if (annotation.matchesDesc(MixinConstants.MODIFY_ARGS)) {
            ModifyArgsOffsetTransformer.modify(methodNode, this.context.insertions);
            return Result.APPLY;
        }

        List<Pair<Integer, Integer>> offsetSwaps = new ArrayList<>(this.context.swaps);
        List<Pair<Integer, Integer>> offsetMoves = new ArrayList<>(this.context.moves);
        LocalVariableNode self = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();
        Deque<Pair<Integer, Type>> insertionQueue = new ArrayDeque<>(this.context.insertions);
        while (!insertionQueue.isEmpty()) {
            Pair<Integer, Type> pair = insertionQueue.pop();
            int index = pair.getFirst();
            Type type = pair.getSecond();
            if (index > newParameterTypes.size() + 1) {
                continue;
            }

            int lvtOrdinal = offset + index;
            int lvtIndex;
            if (index > offset) {
                List<LocalVariableNode> lvt = methodNode.localVariables.stream().sorted(Comparator.comparingInt(lvn -> lvn.index)).toList();
                lvtIndex = lvt.get(lvtOrdinal).index;
            } else {
                lvtIndex = lvtOrdinal;
            }
            int paramOrdinal = isNonStatic && needsOffset ? index + 1 : index;
            ParameterNode newParameter = new ParameterNode(null, Opcodes.ACC_SYNTHETIC);
            newParameterTypes.add(paramOrdinal, type);
            methodNode.parameters.add(paramOrdinal, newParameter);

            int varOffset = AdapterUtil.getLVTOffsetForType(type);
            offsetLVT(methodNode, lvtIndex, varOffset);
            offsetParameters(methodNode, paramOrdinal);

            offsetSwaps.replaceAll(integerIntegerPair -> integerIntegerPair.mapFirst(j -> j >= paramOrdinal ? j + 1 : j));
            offsetMoves.replaceAll(integerIntegerPair -> integerIntegerPair.mapFirst(j -> j >= paramOrdinal ? j + 1 : j).mapSecond(j -> j >= paramOrdinal ? j + 1 : j));

            methodNode.localVariables.add(new LocalVariableNode("adapter_injected_" + paramOrdinal, type.getDescriptor(), null, self.start, self.end, lvtIndex));
        }
        LocalVariableLookup lvtLookup = new LocalVariableLookup(methodNode.localVariables);
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        this.context.replacements.forEach(pair -> {
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
        this.context.substitutes.forEach(pair -> {
            int paramIndex = pair.getFirst();
            int substituteParamIndex = pair.getSecond();
            int localIndex = offset + paramIndex;
            int substituteIndex = offset + substituteParamIndex;
            if (methodNode.parameters.size() > paramIndex) {
                LOGGER.info("Substituting parameter {} for {} in {}.{}", paramIndex, substituteParamIndex, classNode.name, methodNode.name);
                methodNode.parameters.remove(paramIndex);
                newParameterTypes.remove(paramIndex);
                methodNode.localVariables.removeIf(lvn -> lvn.index == localIndex);
                for (AbstractInsnNode insn : methodNode.instructions) {
                    SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                    if (handle != null && handle.get() == localIndex) {
                        handle.set(substituteIndex);
                    }
                }
            }
        });
        IntSet seenSwaps = new IntOpenHashSet();
        for (Pair<Integer, Integer> swapPair : offsetSwaps) {
            int from = swapPair.getFirst();
            if (seenSwaps.contains(from)) {
                continue;
            }
            int to = swapPair.getSecond();
            ParameterNode fromNode = methodNode.parameters.get(from);
            ParameterNode toNode = methodNode.parameters.get(to);
            methodNode.parameters.set(from, toNode);
            methodNode.parameters.set(to, fromNode);
            Type fromType = newParameterTypes.get(from);
            Type toType = newParameterTypes.get(to);
            newParameterTypes.set(from, toType);
            newParameterTypes.set(to, fromType);
            seenSwaps.add(to);
            LOGGER.info(MIXINPATCH, "Swapped parameters at positions {} and {}", from, to);
        }
        if (!offsetSwaps.isEmpty()) {
            for (LocalVariableNode lvn : methodNode.localVariables) {
                for (Pair<Integer, Integer> pair : offsetSwaps) {
                    if (lvn.index == offset + pair.getFirst()) {
                        lvn.index = offset + pair.getSecond();
                        break;
                    } else if (lvn.index == offset + pair.getSecond()) {
                        lvn.index = offset + pair.getFirst();
                        break;
                    }
                }
            }
            for (AbstractInsnNode insn : methodNode.instructions) {
                SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                if (handle != null) {
                    for (Pair<Integer, Integer> pair : offsetSwaps) {
                        if (handle.get() == offset + pair.getFirst()) {
                            handle.set(offset + pair.getSecond());
                            break;
                        } else if (handle.get() == offset + pair.getSecond()) {
                            handle.set(offset + pair.getFirst());
                            break;
                        }
                    }
                }
            }
        }
        if (!this.context.removals.isEmpty()) {
            LOGGER.info(MIXINPATCH, "Removing parameters {} from method {}.{}", this.context.removals, classNode.name, methodNode.name);
        }
        this.context.removals.stream()
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
        this.context.inlines.stream()
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

        methodContext.updateDescription(classNode, methodNode, newParameterTypes);

        return this.context.shouldComputeFrames() ? Result.COMPUTE_FRAMES : Result.APPLY;
    }

    private static Pair<@Nullable ParameterNode, @Nullable LocalVariableNode> removeLocalVariable(MethodNode methodNode, int paramIndex, int lvtOffset, int replaceIndex, List<Type> newParameterTypes) {
        ParameterNode parameter = paramIndex < methodNode.parameters.size() ? methodNode.parameters.remove(paramIndex) : null;
        methodNode.localVariables.sort(Comparator.comparingInt(lvn -> lvn.index));
        LocalVariableNode lvn = methodNode.localVariables.remove(paramIndex + lvtOffset);
        if (lvn != null) {
            int varOffset = AdapterUtil.getLVTOffsetForType(Type.getType(lvn.desc));
            for (LocalVariableNode local : methodNode.localVariables) {
                if (local.index > lvn.index) {
                    local.index -= varOffset;
                }
            }
            for (AbstractInsnNode insn : methodNode.instructions) {
                SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
                if (handle != null) {
                    if (handle.get() == lvn.index) {
                        handle.set(replaceIndex);
                    }
                    if (handle.get() > lvn.index) {
                        handle.set(handle.get() - varOffset);
                    }
                }
            }
        }
        newParameterTypes.remove(paramIndex);
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

    private static void offsetParameters(MethodNode methodNode, int paramIndex) {
        if (methodNode.invisibleParameterAnnotations != null) {
            List<List<AnnotationNode>> annotations = new ArrayList<>(Arrays.asList(methodNode.invisibleParameterAnnotations));
            if (paramIndex < annotations.size()) {
                annotations.add(paramIndex, null);
                methodNode.invisibleParameterAnnotations = (List<AnnotationNode>[]) annotations.toArray(List[]::new);
                methodNode.invisibleAnnotableParameterCount = annotations.size();
            }
        }
        if (methodNode.invisibleTypeAnnotations != null) {
            List<TypeAnnotationNode> invisibleTypeAnnotations = methodNode.invisibleTypeAnnotations;
            for (int j = 0; j < invisibleTypeAnnotations.size(); j++) {
                TypeAnnotationNode typeAnnotation = invisibleTypeAnnotations.get(j);
                TypeReference ref = new TypeReference(typeAnnotation.typeRef);
                int typeIndex = ref.getFormalParameterIndex();
                if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER && typeIndex >= paramIndex) {
                    invisibleTypeAnnotations.set(j, new TypeAnnotationNode(TypeReference.newFormalParameterReference(typeIndex + 1).getValue(), typeAnnotation.typePath, typeAnnotation.desc));
                }
            }
        }
    }

    private static List<AbstractInsnNode> findWrapOperationOriginalCall(MethodNode methodNode, MethodContext methodContext) {
        if (methodContext.methodAnnotation().matchesDesc(MixinConstants.WRAP_OPERATION)) {
            List<AbstractInsnNode> list = new ArrayList<>();
            outer:
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof MethodInsnNode minsn && WO_ORIGINAL_CALL.matches(minsn)) {
                    for (AbstractInsnNode prev = insn.getPrevious(); prev != null; prev = prev.getPrevious()) {
                        if (prev instanceof LabelNode) {
                            continue outer;
                        }
                        if (AdapterUtil.canHandleLocalVarInsnValue(prev)) {
                            list.add(prev);
                        }
                    }
                }
            }
            return List.copyOf(list);
        }
        return List.of();
    }

    public enum TargetType {
        ALL,
        METHOD(MixinConstants.INJECT, MixinConstants.OVERWRITE, MixinConstants.MODIFY_VAR),
        INJECTION_POINT(MixinConstants.REDIRECT, MixinConstants.MODIFY_ARG, MixinConstants.MODIFY_ARGS);

        public static final Codec<TargetType> CODEC = Codec.STRING.xmap(TargetType::from, TargetType::name);

        private final Set<String> targetMixinTypes;

        TargetType(String... targetMixinTypes) {
            this.targetMixinTypes = new HashSet<>(Arrays.asList(targetMixinTypes));
        }

        public static TargetType from(String name) {
            return valueOf(name.toUpperCase(Locale.ROOT));
        }

        public Set<String> getTargetMixinTypes() {
            return this.targetMixinTypes;
        }
    }

    public interface LVTFixer {
        void accept(int index, AbstractInsnNode insn, InsnList list);
    }

    public record ParamsContext(
        List<Pair<Integer, Type>> insertions,
        List<Pair<Integer, Type>> replacements,
        List<Pair<Integer, Integer>> swaps,
        List<Pair<Integer, Integer>> substitutes,
        List<Integer> removals,
        List<Pair<Integer, Integer>> moves,
        List<Pair<Integer, Consumer<InstructionAdapter>>> inlines
    ) {
        public static final Codec<Pair<Integer, Type>> MODIFICATION_CODEC = Codec.pair(
            Codec.INT.fieldOf("index").codec(),
            AdapterUtil.TYPE_CODEC.fieldOf("type").codec()
        );
        public static final Codec<Pair<Integer, Integer>> SWAP_CODEC = Codec.pair(
            Codec.INT.fieldOf("original").codec(),
            Codec.INT.fieldOf("replacement").codec()
        );
        public static final Codec<ParamsContext> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MODIFICATION_CODEC.listOf().optionalFieldOf("insertions", List.of()).forGetter(ParamsContext::insertions),
            MODIFICATION_CODEC.listOf().optionalFieldOf("replacements", List.of()).forGetter(ParamsContext::replacements),
            SWAP_CODEC.listOf().optionalFieldOf("swaps", List.of()).forGetter(ParamsContext::swaps)
        ).apply(instance, (insertions, replacements, swaps) ->
            new ParamsContext(insertions, replacements, swaps, List.of(), List.of(), List.of(), List.of())));

        public static ParamsContext create(ParametersDiff diff) {
            return new ParamsContext(diff.insertions(), diff.replacements(), diff.swaps(), List.of(), diff.removals(), diff.moves(), List.of());
        }

        public boolean isEmpty() {
            return this.insertions.isEmpty() && this.replacements.isEmpty() && this.swaps.isEmpty() && this.substitutes.isEmpty() && this.removals.isEmpty() && this.moves.isEmpty() && this.inlines.isEmpty();
        }

        public boolean shouldComputeFrames() {
            return !this.swaps.isEmpty() || !this.replacements.isEmpty() || !this.substitutes.isEmpty() || !this.removals.isEmpty();
        }
    }

    public static class Builder {
        private final List<Pair<Integer, Type>> insertions = new ArrayList<>();
        private final List<Pair<Integer, Type>> replacements = new ArrayList<>();
        private final List<Pair<Integer, Integer>> substitutes = new ArrayList<>();
        private final List<Integer> removals = new ArrayList<>();
        private final List<Pair<Integer, Integer>> swap = new ArrayList<>();
        private final List<Pair<Integer, Consumer<InstructionAdapter>>> inlines = new ArrayList<>();
        private TargetType targetType = TargetType.ALL;
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

        public Builder targetType(TargetType targetType) {
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
            ParamsContext context = new ParamsContext(this.insertions, this.replacements, this.swap, this.substitutes, this.removals, List.of(), this.inlines);
            return new ModifyMethodParams(context, this.targetType, this.ignoreOffset, this.lvtFixer);
        }
    }
}
