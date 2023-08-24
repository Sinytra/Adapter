package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.*;

import static dev.su5ed.sinytra.adapter.patch.PatchInstance.MIXINPATCH;

public record ModifyMethodParams(List<Pair<Integer, Type>> insertions, List<Pair<Integer, Type>> replacements, boolean targetInjectionPoint,
                                 @Nullable LVTFixer lvtFixer) implements MethodTransform {
    public static final Codec<Pair<Integer, Type>> MODIFICATION_CODEC = Codec.pair(
        Codec.INT.fieldOf("index").codec(),
        CodecUtil.TYPE_CODEC.fieldOf("type").codec()
    );
    public static final Codec<ModifyMethodParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MODIFICATION_CODEC.listOf().optionalFieldOf("insertions", List.of()).forGetter(ModifyMethodParams::insertions),
        MODIFICATION_CODEC.listOf().optionalFieldOf("replacements", List.of()).forGetter(ModifyMethodParams::replacements),
        Codec.BOOL.optionalFieldOf("targetInjectionPoint", false).forGetter(ModifyMethodParams::targetInjectionPoint)
    ).apply(instance, (insertions, replacements, targetInjectionPoint) -> new ModifyMethodParams(insertions, replacements, targetInjectionPoint, null)));

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> METHOD_TARGETS = Set.of(Patch.INJECT, Patch.OVERWRITE, Patch.MODIFY_VAR);
    private static final Set<String> INJECTION_TARGETS = Set.of(Patch.REDIRECT, Patch.MODIFY_ARG);

    public static ModifyMethodParams create(String cleanMethodDesc, String dirtyMethodDesc, boolean targetInjectionPoint) {
        ParametersDiff diff = ParametersDiff.compareTypeParameters(Type.getArgumentTypes(cleanMethodDesc), Type.getArgumentTypes(dirtyMethodDesc));
        return new ModifyMethodParams(diff.insertions(), diff.replacements(), targetInjectionPoint, null);
    }

    public static ModifyMethodParams create(ParametersDiff diff, boolean targetInjectionPoint) {
        return new ModifyMethodParams(diff.insertions(), diff.replacements(), targetInjectionPoint, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ModifyMethodParams {
        if (insertions.isEmpty() && replacements.isEmpty()) {
            throw new IllegalArgumentException("Method parameter transformation contains no changes");
        }
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return this.targetInjectionPoint ? INJECTION_TARGETS : METHOD_TARGETS;
    }

    @Override
    public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        List<Type> newParameterTypes = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(methodNode.desc)));
        int offset = (methodNode.access & Opcodes.ACC_STATIC) == 0
            // If it's a redirect, the first param (index 1) is the object instance
            ? annotation.desc.equals(Patch.REDIRECT) ? 2 : 1
            : 0;

        if (annotation.desc.equals(Patch.MODIFY_VAR)) {
            AnnotationValueHandle<Integer> indexHandle = (AnnotationValueHandle<Integer>) annotationValues.get("index");
            if (indexHandle != null) {
                this.insertions.forEach(pair -> {
                    int localIndex = offset + pair.getFirst();
                    int indexValue = indexHandle.get();
                    if (localIndex >= indexValue) {
                        indexHandle.set(indexValue + 1);
                    }
                });
            }
            return true;
        }

        LocalVariableNode self = methodNode.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElseThrow();
        Deque<Pair<Integer, Type>> insertionQueue = new ArrayDeque<>(this.insertions);
        while (!insertionQueue.isEmpty()) {
            Pair<Integer, Type> pair = insertionQueue.pop();
            int index = pair.getFirst();
            Type type = pair.getSecond();
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
            if (index < methodNode.parameters.size()) methodNode.parameters.add(index, newParameter);
            else methodNode.parameters.add(newParameter);
            for (LocalVariableNode localVariable : methodNode.localVariables) {
                if (localVariable.index >= lvtIndex) {
                    localVariable.index++;
                }
            }
            methodNode.localVariables.add(new LocalVariableNode("adapter_injected_" + index, type.getDescriptor(), null, self.start, self.end, lvtIndex));

            // TODO All visible/invisible annotations
            if (methodNode.invisibleParameterAnnotations != null) {
                List<List<AnnotationNode>> annotations = new ArrayList<>(Arrays.asList(methodNode.invisibleParameterAnnotations));
                if (index < annotations.size()) {
                    annotations.add(index, null);
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
                    if (ref.getSort() == TypeReference.METHOD_FORMAL_PARAMETER && typeIndex >= index) {
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
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof VarInsnNode varInsnNode && varInsnNode.var >= lvtIndex) {
                    varInsnNode.var++;
                }
            }
        }
        this.replacements.forEach(pair -> {
            int index = pair.getFirst();
            Type type = pair.getSecond();
            newParameterTypes.set(index, type);
            LocalVariableNode localVar = methodNode.localVariables.get(offset + index);
            localVar.desc = type.getDescriptor();
            localVar.signature = null;
        });
        if (!this.replacements.isEmpty() && this.lvtFixer != null) {
            //noinspection ForLoopReplaceableByForEach
            for (ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); ) {
                AbstractInsnNode insn = iterator.next();
                if (insn instanceof VarInsnNode varInsn && this.replacements.stream().anyMatch(pair -> offset + pair.getFirst() == varInsn.var)) {
                    this.lvtFixer.accept(varInsn.var, varInsn, methodNode.instructions);
                }
            }
        }

        Type returnType = Type.getReturnType(methodNode.desc);
        String newDesc = Type.getMethodDescriptor(returnType, newParameterTypes.toArray(Type[]::new));
        LOGGER.info(MIXINPATCH, "Changing descriptor of method {}.{}{} to {}", classNode.name, methodNode.name, methodNode.desc, newDesc);
        methodNode.desc = newDesc;

        return true;
    }

    public static class Builder {
        private final List<Pair<Integer, Type>> insertions = new ArrayList<>();
        private final List<Pair<Integer, Type>> replacements = new ArrayList<>();
        private boolean targetInjectionPoint;
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

        public Builder targetInjectionPoint() {
            this.targetInjectionPoint = true;
            return this;
        }

        public Builder lvtFixer(LVTFixer lvtFixer) {
            this.lvtFixer = lvtFixer;
            return this;
        }

        public ModifyMethodParams build() {
            return new ModifyMethodParams(this.insertions, this.replacements, this.targetInjectionPoint, this.lvtFixer);
        }
    }
}
