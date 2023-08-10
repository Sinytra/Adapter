package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypeReference;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Function;

import static dev.su5ed.sinytra.adapter.patch.PatchImpl.PATCHER;

public record ModifyMethodParams(List<AddParameter> changes, @Nullable LVTFixer lvtFixer) implements MethodTransform {
    public record AddParameter(int index, Type type) {
        public static final Codec<AddParameter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("index").forGetter(AddParameter::index),
            PatchSerialization.TYPE_CODEC.fieldOf("type").forGetter(AddParameter::type)
        ).apply(instance, AddParameter::new));
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Codec<ModifyMethodParams> CODEC = RecordCodecBuilder
        .<ModifyMethodParams>create(instance -> instance.group(
            AddParameter.CODEC.listOf().fieldOf("changes").forGetter(ModifyMethodParams::changes)
        ).apply(instance, changes -> new ModifyMethodParams(changes, null)))
        .comapFlatMap(obj -> obj.lvtFixer != null ? DataResult.error(() -> "Cannot serialize lvtFixer") : DataResult.success(obj), Function.identity());

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(PatchImpl.INJECT_ANN, PatchImpl.MODIFY_ARG_ANN, PatchImpl.OVERWRITE_ANN);
    }

    @Override
    public boolean apply(ClassNode classNode, MethodNode methodNode, AnnotationNode annotation, Map<String, AnnotationValueHandle<?>> annotationValues, PatchContext context) {
        Type[] parameterTypes = Type.getArgumentTypes(methodNode.desc);
        List<Type> list = new ArrayList<>(Arrays.asList(parameterTypes));
        for (AddParameter change : this.changes) {
            int normalIndex = change.index < change.index ? list.indexOf(parameterTypes[change.index]) : change.index;
            list.add(normalIndex, change.type);
        }
        Type[] newParameterTypes = list.toArray(Type[]::new);
        Type returnType = Type.getReturnType(methodNode.desc);
        String newDesc = Type.getMethodDescriptor(returnType, newParameterTypes);
        LOGGER.info(PATCHER, "Changing descriptor of method {}.{}{} to {}", classNode.name, methodNode.name, methodNode.desc, newDesc);
        Int2ObjectMap<Type> insertionIndices = new Int2ObjectOpenHashMap<>();
        Int2ObjectMap<Type> replacementIndices = new Int2ObjectOpenHashMap<>();
        int offset = (methodNode.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

        int i = 0;
        for (int j = 0; j < newParameterTypes.length && i < methodNode.parameters.size(); j++) {
            Type type = newParameterTypes[j];
            if (!parameterTypes[i].equals(type)) {
                if (i == j && this.lvtFixer != null) {
                    replacementIndices.put(offset + j, type);
                } else {
                    insertionIndices.put(j, type);
                    continue;
                }
            }
            i++;
        }
        if (i != methodNode.parameters.size() && this.lvtFixer == null) {
            throw new RuntimeException("Unable to patch LVT capture, incompatible parameters");
        }
        insertionIndices.forEach((index, type) -> {
            ParameterNode newParameter = new ParameterNode(null, Opcodes.ACC_SYNTHETIC);
            if (index < methodNode.parameters.size()) methodNode.parameters.add(index, newParameter);
            else methodNode.parameters.add(newParameter);

            int localIndex = offset + index;
            for (LocalVariableNode localVariable : methodNode.localVariables) {
                if (localVariable.index >= localIndex) {
                    localVariable.index++;
                }
            }
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
                        if (annoIndex >= localIndex) {
                            annotationIndices.set(j, annoIndex + 1);
                        }
                    }
                }
            }
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof VarInsnNode varInsnNode && varInsnNode.var >= localIndex) {
                    varInsnNode.var++;
                }
            }
        });
        replacementIndices.forEach((index, type) -> {
            LocalVariableNode localVar = methodNode.localVariables.get(index);
            localVar.desc = type.getDescriptor();
            localVar.signature = null;
        });
        if (!replacementIndices.isEmpty() && this.lvtFixer != null) {
            //noinspection ForLoopReplaceableByForEach
            for (ListIterator<AbstractInsnNode> iterator = methodNode.instructions.iterator(); iterator.hasNext(); ) {
                AbstractInsnNode insn = iterator.next();
                if (insn instanceof VarInsnNode varInsn && replacementIndices.containsKey(varInsn.var)) {
                    this.lvtFixer.accept(varInsn.var, varInsn, methodNode.instructions);
                }
            }
        }
        methodNode.desc = newDesc;
        return true;
    }
}
