package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;
import dev.su5ed.sinytra.adapter.patch.PatchContext;
import dev.su5ed.sinytra.adapter.patch.PatchInstance;
import dev.su5ed.sinytra.adapter.patch.analysis.ParametersDiff;
import dev.su5ed.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import dev.su5ed.sinytra.adapter.patch.fixes.FieldTypeFix;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.ExtraCodecs;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.*;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ModifyMethodParams(List<Pair<Integer, Type>> insertions, List<Pair<Integer, Type>> replacements, List<Pair<Integer, Integer>> swaps,
                                 List<Pair<Integer, Integer>> substitutes, List<Integer> removals, TargetType targetType, boolean ignoreOffset, @Nullable LVTFixer lvtFixer) implements MethodTransform {
    public static final Codec<Pair<Integer, Type>> MODIFICATION_CODEC = Codec.pair(
        Codec.INT.fieldOf("index").codec(),
        ExtraCodecs.TYPE_CODEC.fieldOf("type").codec()
    );
    public static final Codec<Pair<Integer, Integer>> SWAP_CODEC = Codec.pair(
        Codec.INT.fieldOf("original").codec(),
        Codec.INT.fieldOf("replacement").codec()
    );
    public static final Codec<ModifyMethodParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MODIFICATION_CODEC.listOf().optionalFieldOf("insertions", List.of()).forGetter(ModifyMethodParams::insertions),
        MODIFICATION_CODEC.listOf().optionalFieldOf("replacements", List.of()).forGetter(ModifyMethodParams::replacements),
        SWAP_CODEC.listOf().optionalFieldOf("swaps", List.of()).forGetter(ModifyMethodParams::swaps),
        TargetType.CODEC.optionalFieldOf("targetInjectionPoint", TargetType.ALL).forGetter(ModifyMethodParams::targetType)
    ).apply(instance, (insertions, replacements, swaps, targetInjectionPoint) -> new ModifyMethodParams(insertions, replacements, swaps, List.of(), List.of(), targetInjectionPoint, false, null)));

    private static final Logger LOGGER = LogUtils.getLogger();

    public static ModifyMethodParams create(String cleanMethodDesc, String dirtyMethodDesc, TargetType targetType) {
        ParametersDiff diff = ParametersDiff.compareTypeParameters(Type.getArgumentTypes(cleanMethodDesc), Type.getArgumentTypes(dirtyMethodDesc));
        return new ModifyMethodParams(diff.insertions(), diff.replacements(), diff.swaps(), List.of(), diff.removals(), targetType, false, null);
    }

    public static ModifyMethodParams create(ParametersDiff diff, TargetType targetType) {
        return new ModifyMethodParams(diff.insertions(), diff.replacements(), diff.swaps(), List.of(), diff.removals(), targetType, false, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModifyMethodParams {
        if (insertions.isEmpty() && replacements.isEmpty() && swaps.isEmpty() && substitutes.isEmpty() && removals.isEmpty()) {
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
        int offset = (methodNode.access & Opcodes.ACC_STATIC) == 0
            // If it's a redirect, the first param (index 1) is the object instance
            ? annotation.matchesDesc(Patch.REDIRECT) && !this.ignoreOffset ? 2 : 1
            : 0;

        if (annotation.matchesDesc(Patch.MODIFY_VAR)) {
            AnnotationValueHandle<Integer> indexHandle = annotation.<Integer>getValue("index").orElse(null);
            if (indexHandle != null) {
                this.insertions.forEach(pair -> {
                    int localIndex = offset + pair.getFirst();
                    int indexValue = indexHandle.get();
                    if (localIndex >= indexValue) {
                        indexHandle.set(indexValue + 1);
                    }
                });
            }
            return Result.APPLY;
        }
        if (annotation.matchesDesc(Patch.MODIFY_ARGS)) {
            ModifyArgsOffsetTransformer.modify(methodNode, this.insertions);
            return Result.APPLY;
        }

        List<Pair<Integer, Integer>> offsetSwaps = new ArrayList<>(this.swaps);
        LocalVariableNode self = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();
        Deque<Pair<Integer, Type>> insertionQueue = new ArrayDeque<>(this.insertions);
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
            ParameterNode newParameter = new ParameterNode(null, Opcodes.ACC_SYNTHETIC);
            newParameterTypes.add(index, type);
            methodNode.parameters.add(index, newParameter);

            int varOffset = AdapterUtil.getLVTOffsetForType(type);
            offsetLVT(methodNode, index, lvtIndex, varOffset);

            offsetSwaps.replaceAll(integerIntegerPair -> integerIntegerPair.mapFirst(j -> j >= index ? j + 1 : j));

            methodNode.localVariables.add(new LocalVariableNode("adapter_injected_" + index, type.getDescriptor(), null, self.start, self.end, lvtIndex));
        }
        BytecodeFixerUpper bfu = context.getEnvironment().getBytecodeFixerUpper();
        this.replacements.forEach(pair -> {
            int index = pair.getFirst();
            Type type = pair.getSecond();
            newParameterTypes.set(index, type);
            // FIXME Not actually accurate; account for wide types double & long
            int localIndex = offset + index;
            LocalVariableNode localVar = methodNode.localVariables.stream().filter(lvn -> lvn.index == localIndex).findFirst().orElseThrow();
            Type originalType = Type.getType(localVar.desc);
            localVar.desc = type.getDescriptor();
            localVar.signature = null;
            if (type.getSort() == Type.OBJECT && originalType.getSort() == Type.OBJECT) {
                // Replace variable usages with the new type
                for (AbstractInsnNode insn : methodNode.instructions) {
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
                            FieldTypeFix typeFix = bfu.getFieldTypeAdapter(type, originalType);
                            if (typeFix != null) {
                                typeFix.typePatch().apply(methodNode.instructions, varInsn);
                            }
                        }
                        if (this.lvtFixer != null) {
                            this.lvtFixer.accept(varInsn.var, varInsn, methodNode.instructions);
                        }
                    }
                }
            }
        });
        this.substitutes.forEach(pair -> {
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
                    if (insn instanceof VarInsnNode varInsn && varInsn.var == localIndex) {
                        varInsn.var = substituteIndex;
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
                if (insn instanceof VarInsnNode varInsn) {
                    for (Pair<Integer, Integer> pair : offsetSwaps) {
                        if (varInsn.var == offset + pair.getFirst()) {
                            varInsn.var = offset + pair.getSecond();
                            break;
                        } else if (varInsn.var == offset + pair.getSecond()) {
                            varInsn.var = offset + pair.getFirst();
                            break;
                        }
                    }
                }
            }
        }
        this.removals.stream()
            .sorted(Comparator.<Integer>comparingInt(i -> i).reversed())
            .forEach(removal -> {
                LOGGER.info(MIXINPATCH, "Removing parameter {} from method {}.{}", removal, classNode.name, methodNode.name);
                if (removal < methodNode.parameters.size()) {
                    methodNode.parameters.remove(removal.intValue());
                }
                methodNode.localVariables.sort(Comparator.comparingInt(lvn -> lvn.index));
                LocalVariableNode lvn = methodNode.localVariables.remove(removal + offset);
                if (lvn != null) {
                    for (LocalVariableNode local : methodNode.localVariables) {
                        if (local.index >= lvn.index) {
                            local.index--;
                        }
                    }
                    for (AbstractInsnNode insn : methodNode.instructions) {
                        if (insn instanceof VarInsnNode varInsn && varInsn.var >= lvn.index) {
                            varInsn.var--;
                        }
                    }
                }
                newParameterTypes.remove(removal.intValue());
            });

        Type returnType = Type.getReturnType(methodNode.desc);
        String newDesc = Type.getMethodDescriptor(returnType, newParameterTypes.toArray(Type[]::new));
        LOGGER.info(MIXINPATCH, "Changing descriptor of method {}.{}{} to {}", classNode.name, methodNode.name, methodNode.desc, newDesc);
        methodNode.desc = newDesc;
        methodNode.signature = null;

        return !this.swaps.isEmpty() || !this.replacements.isEmpty() || !this.substitutes.isEmpty() || !this.removals.isEmpty() ? Result.COMPUTE_FRAMES : Result.APPLY;
    }

    private static void offsetLVT(MethodNode methodNode, int paramIndex, int lvtIndex, int offset) {
        for (LocalVariableNode localVariable : methodNode.localVariables) {
            if (localVariable.index >= lvtIndex) {
                localVariable.index += offset;
            }
        }

        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsnNode && varInsnNode.var >= lvtIndex) {
                varInsnNode.var += offset;
            }
        }

        // TODO All visible/invisible annotations
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

    public enum TargetType {
        ALL,
        METHOD(Patch.INJECT, Patch.OVERWRITE, Patch.MODIFY_VAR),
        INJECTION_POINT(Patch.REDIRECT, Patch.MODIFY_ARG, Patch.MODIFY_ARGS);

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

    public static class Builder {
        private final List<Pair<Integer, Type>> insertions = new ArrayList<>();
        private final List<Pair<Integer, Type>> replacements = new ArrayList<>();
        private final List<Pair<Integer, Integer>> substitutes = new ArrayList<>();
        private final List<Pair<Integer, Integer>> swap = new ArrayList<>();
        private TargetType targetType = TargetType.ALL;
        private boolean ignoreOffset = false;
        @Nullable
        private LVTFixer lvtFixer;

        public Builder insert(int index, Type type) {
            this.insertions.add(Pair.of(index, type));
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

        public ModifyMethodParams build() {
            return new ModifyMethodParams(this.insertions, this.replacements, this.swap, this.substitutes, List.of(), this.targetType, this.ignoreOffset, this.lvtFixer);
        }
    }
}
