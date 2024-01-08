package dev.su5ed.sinytra.adapter.patch.fixes;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.api.ClassTransform;
import dev.su5ed.sinytra.adapter.patch.api.GlobalReferenceMapper;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import dev.su5ed.sinytra.adapter.patch.api.PatchContext;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FieldTypeUsageTransformer implements ClassTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        boolean applied = false;
        if (bfu != null) {
            Map<String, Pair<Type, Type>> classUpdatedTypes = new HashMap<>();
            // Update class field types
            if (context.targetTypes().size() == 1) {
                Type targetType = context.targetTypes().get(0);
                for (FieldNode field : classNode.fields) {
                    if (AdapterUtil.isShadowField(field)) {
                        Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(targetType.getInternalName(), GlobalReferenceMapper.remapReference(field.name));
                        if (updatedTypes != null) {
                            field.desc = updatedTypes.getSecond().getDescriptor();
                            // Update shadow field usages
                            classUpdatedTypes.put(field.name, updatedTypes);
                        }
                    }
                }
            }
            // Update field insn types
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof FieldInsnNode finsn) {
                        // Update usages of fields that belong to this class
                        if (finsn.owner.equals(classNode.name)) {
                            Pair<Type, Type> classUpdatedType = classUpdatedTypes.get(finsn.name);
                            if (classUpdatedType != null) {
                                applied |= runFieldFix(bfu, classUpdatedType, method, finsn);
                                continue;
                            }
                        }

                        // Update used fields of other classes
                        Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(finsn.owner, GlobalReferenceMapper.remapReference(finsn.name));
                        if (updatedTypes != null) {
                            applied |= runFieldFix(bfu, updatedTypes, method, finsn);
                        }
                    }
                }
                // Search for method calls made on modified class fields and update their owners to match the new field types
                List<Pair<FieldInsnNode, MethodInsnNode>> results = MethodCallAnalyzer.analyzeMethod(method, (m, v) -> m.getOpcode() == Opcodes.INVOKEVIRTUAL, (insn, values) -> {
                    if (!values.isEmpty()) {
                        AbstractInsnNode valueInsn = MethodCallAnalyzer.getSingleInsn(values, 0);
                        if (valueInsn instanceof FieldInsnNode finsn) {
                            return Pair.of(finsn, insn);
                        }
                    }
                    return null;
                });
                for (Pair<FieldInsnNode, MethodInsnNode> insn : results) {
                    FieldInsnNode finsn = insn.getFirst();
                    if (finsn.owner.equals(classNode.name)) {
                        Pair<Type, Type> classUpdatedType = classUpdatedTypes.get(finsn.name);
                        if (classUpdatedType != null) {
                            insn.getSecond().owner = classUpdatedType.getSecond().getInternalName();
                            applied = true;
                        }
                    }
                }
            }
        }
        return applied ? Patch.Result.APPLY : Patch.Result.PASS;
    }

    private static boolean runFieldFix(BytecodeFixerUpper bfu, Pair<Type, Type> updatedTypes, MethodNode method, FieldInsnNode finsn) {
        TypeAdapter typeAdapter = bfu.getTypeAdapter(updatedTypes.getSecond(), updatedTypes.getFirst());
        if (typeAdapter != null) {
            LOGGER.info("Running fixup for field {}.{}{} in method {}{}", finsn.owner, finsn.name, finsn.desc, method.name, method.desc);
            finsn.desc = updatedTypes.getSecond().getDescriptor();
            typeAdapter.apply(method.instructions, finsn);
            return true;
        }
        return false;
    }
}
