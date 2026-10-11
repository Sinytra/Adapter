package org.sinytra.adapter.types;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.transform.ClassTransformer;
import org.sinytra.adapter.env.ctx.PatchContext;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.sinytra.adapter.util.AdapterUtil.MIXINPATCH;

public class FieldTypeUsageTransformer implements ClassTransformer {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        boolean applied = false;
        if (bfu != null) {
            Map<String, Pair<Type, Type>> classUpdatedTypes = new HashMap<>();
            // Update class field types
            if (context.targetTypes().size() == 1) {
                Type targetType = context.targetTypes().getFirst();
                for (FieldNode field : classNode.fields) {
                    if (AdapterUtil.isShadowField(field)) {
                        Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(targetType.getInternalName(), field.name);
                        if (updatedTypes != null) {
                            field.desc = updatedTypes.getSecond().getDescriptor();
                            // Update shadow field usages
                            classUpdatedTypes.put(field.name, updatedTypes);
                            applied = true;
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
                        Pair<Type, Type> updatedTypes = bfu.getFieldTypeChange(finsn.owner, finsn.name);
                        if (updatedTypes != null) {
                            applied |= runFieldFix(bfu, updatedTypes, method, finsn);
                        }
                    }
                }
                // Search for method calls made on modified class fields and update their owners to match the new field types
                if (!classUpdatedTypes.isEmpty()) {
                    AtomicBoolean retargeted = new AtomicBoolean();
                    MethodCallAnalyzer.forEachReceiver(method, (call, receiver) -> {
                        if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && AdapterUtil.getSingleOrNull(receiver) instanceof FieldInsnNode finsn
                            && finsn.owner.equals(classNode.name)
                        ) {
                            Pair<Type, Type> classUpdatedType = classUpdatedTypes.get(finsn.name);
                            if (classUpdatedType != null) {
                                call.owner = classUpdatedType.getSecond().getInternalName();
                                retargeted.set(true);
                            }
                        }
                    });
                    applied |= retargeted.get();
                }
            }
        }
        return applied ? PatchResult.APPLY : PatchResult.PASS;
    }

    private static boolean runFieldFix(BytecodeFixerUpper bfu, Pair<Type, Type> updatedTypes, MethodNode method, FieldInsnNode finsn) {
        boolean isStore = finsn.getOpcode() == Opcodes.PUTFIELD || finsn.getOpcode() == Opcodes.PUTSTATIC;
        TypeAdapter typeAdapter = isStore
            ? bfu.getTypeAdapter(updatedTypes.getFirst(), updatedTypes.getSecond())
            : bfu.getTypeAdapter(updatedTypes.getSecond(), updatedTypes.getFirst());
        if (typeAdapter == null || isStore && finsn.getPrevious() == null) {
            return false;
        }

        LOGGER.debug(MIXINPATCH, "Running fixup for field {}.{}{} in method {}{}", finsn.owner, finsn.name, finsn.desc, method.name, method.desc);
        finsn.desc = updatedTypes.getSecond().getDescriptor();
        typeAdapter.apply(method.instructions, isStore ? finsn.getPrevious() : finsn);
        return true;
    }
}
