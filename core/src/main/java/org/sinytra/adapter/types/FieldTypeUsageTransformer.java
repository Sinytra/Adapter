package org.sinytra.adapter.types;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.flow.DataFlow;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.api.BytecodeFixerUpper;
import org.sinytra.adapter.api.ClassPatchContext;
import org.sinytra.adapter.api.ClassTransformer;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MixinBridge;

import java.util.HashMap;
import java.util.Map;

public class FieldTypeUsageTransformer implements ClassTransformer {
    @Override
    public PatchResult apply(ClassNode classNode, ClassPatchContext context) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        if (bfu == null) {
            return PatchResult.PASS;
        }

        Type target = context.singleTarget();
        Map<String, Pair<Type, Type>> shadowChanges = target != null ? retypeShadowFields(classNode, target, bfu) : Map.of();
        boolean applied = !shadowChanges.isEmpty();
        for (MethodNode method : classNode.methods) {
            applied |= target != null && redirectAccessor(classNode, method, target, bfu, context);
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode fieldInsn) {
                    Pair<Type, Type> change = fieldInsn.owner.equals(classNode.name) ? shadowChanges.get(fieldInsn.name)
                        : bfu.getFieldTypeChange(fieldInsn.owner, fieldInsn.name);
                    applied |= change != null && adaptFieldInsn(bfu, change, method, fieldInsn, context, classNode);
                }
            }
            if (!shadowChanges.isEmpty()) {
                applied |= retargetReceivers(classNode, method, shadowChanges);
            }
        }
        return applied ? PatchResult.APPLY : PatchResult.PASS;
    }

    private static Map<String, Pair<Type, Type>> retypeShadowFields(ClassNode classNode, Type target, BytecodeFixerUpper bfu) {
        Map<String, Pair<Type, Type>> changes = new HashMap<>();
        for (FieldNode field : classNode.fields) {
            Pair<Type, Type> change = AdapterUtil.isShadowField(field) ? bfu.getFieldTypeChange(target.getInternalName(), field.name) : null;
            if (change != null && field.desc.equals(change.getFirst().getDescriptor())) {
                field.desc = change.getSecond().getDescriptor();
                changes.put(field.name, change);
            }
        }
        return changes;
    }

    private boolean adaptFieldInsn(BytecodeFixerUpper bfu, Pair<Type, Type> change, MethodNode method, FieldInsnNode fieldInsn, ClassPatchContext context,
                                   ClassNode classNode) {
        boolean isStore = fieldInsn.getOpcode() == Opcodes.PUTFIELD || fieldInsn.getOpcode() == Opcodes.PUTSTATIC;
        TypeAdapter adapter = isStore ? bfu.getTypeAdapter(change.getFirst(), change.getSecond()) : bfu.getTypeAdapter(change.getSecond(), change.getFirst());
        if (adapter == null || !fieldInsn.desc.equals(change.getFirst().getDescriptor()) || isStore && fieldInsn.getPrevious() == null) {
            return false;
        }

        fieldInsn.desc = change.getSecond().getDescriptor();
        adapter.apply(method.instructions, isStore ? fieldInsn.getPrevious() : fieldInsn);
        context.environment().auditTrail().recordAudit(this, classNode, "Adapt %s of field %s.%s in %s%s to type %s", isStore ? "store" : "load",
            fieldInsn.owner, fieldInsn.name, method.name, method.desc, change.getSecond());
        return true;
    }

    private static boolean retargetReceivers(ClassNode classNode, MethodNode method, Map<String, Pair<Type, Type>> shadowChanges) {
        boolean[] retargeted = {false};
        DataFlow.of(method).forEachReceiver((call, receiver) -> {
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL && AdapterUtil.getSingleOrNull(receiver) instanceof FieldInsnNode fieldInsn
                && fieldInsn.owner.equals(classNode.name) && shadowChanges.containsKey(fieldInsn.name)
            ) {
                call.owner = shadowChanges.get(fieldInsn.name).getSecond().getInternalName();
                retargeted[0] = true;
            }
        });
        return retargeted[0];
    }

    private boolean redirectAccessor(ClassNode classNode, MethodNode method, Type target, BytecodeFixerUpper bfu, ClassPatchContext context) {
        AnnotationNode accessor = AdapterUtil.getAnnotation(method.visibleAnnotations, MixinAnnotations.ACCESSOR);
        String field = accessor != null && Type.getArgumentCount(method.desc) == 0 && Type.getReturnType(method.desc) != Type.VOID_TYPE
            ? getAccessorField(classNode, method, accessor, context) : null;
        Pair<Type, Type> change = field != null ? bfu.getFieldTypeChange(target.getInternalName(), field) : null;
        TypeAdapter adapter = change != null ? bfu.getTypeAdapter(change.getSecond(), change.getFirst()) : null;
        if (adapter == null) {
            return false;
        }

        String getter = AccessorAdapterGenerator.getOrCreateGetter(bfu.getGenerator(), target, field, MethodAnalyzer.isStatic(method), adapter);
        method.visibleAnnotations.remove(accessor);
        method.visitAnnotation(MixinAnnotations.INVOKER, true).visit("value", getter);
        context.environment().auditTrail().recordAudit(this, classNode, "Redirect accessor %s%s to invoker %s", method.name, method.desc, getter);
        return true;
    }

    @Nullable
    private static String getAccessorField(ClassNode classNode, MethodNode method, AnnotationNode accessor, ClassPatchContext context) {
        String value = AdapterUtil.getAnnotationValue(accessor, "value");
        String name = value != null && !value.isEmpty() ? value : MixinBridge.getAccessorFieldName(method.name);
        if (name == null) {
            return null;
        }
        String remapped = context.environment().refmapHolder().remap(classNode.name, name);
        int descIndex = remapped.indexOf(':');
        return descIndex == -1 ? remapped : remapped.substring(0, descIndex);
    }
}
