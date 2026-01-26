package org.sinytra.adapter.next.transform.cls;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ctx.PatchContext;
import org.sinytra.adapter.next.env.ctx.PatchResult;
import org.sinytra.adapter.next.env.util.MixinAnnotations;
import org.sinytra.adapter.next.transform.ClassTransformer;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DynamicAnonymousShadowFieldTypePatch implements ClassTransformer {
    @Override
    public PatchResult apply(ClassNode classNode, ClassTarget classTarget, PatchContext context) {
        Type singleTarget = classTarget.getSingle();
        String target = singleTarget.getInternalName();
        if (!AdapterUtil.isAnonymousClass(target)) {
            return PatchResult.PASS;
        }

        ClassNode targetClass = context.environment().dirtyClassLookup().getClass(target).orElse(null);
        if (targetClass == null) {
            return PatchResult.PASS;
        }

        Map<String, String> renames = new HashMap<>();
        Multimap<String, FieldNode> fields = HashMultimap.create();
        for (FieldNode targetField : targetClass.fields) {
            fields.put(targetField.desc, targetField);
        }

        for (FieldNode field : classNode.fields) {
            if (field.visibleAnnotations != null) {
                for (AnnotationNode ann : field.visibleAnnotations) {
                    if (MixinAnnotations.SHADOW.equals(ann.desc)) {
                        Collection<FieldNode> targetFields = fields.get(field.desc);
                        if (targetFields.size() == 1) {
                            FieldNode targetField = targetFields.iterator().next();
                            if (!field.name.equals(targetField.name)) {
                                renames.put(field.name, targetField.name);
                                field.name = targetField.name;
                            }
                        }
                    }
                }
            }
        }

        if (!renames.isEmpty()) {
            renames.forEach((from, to) -> context.environment().auditTrail().recordAudit(this, classNode, "Rename anonymous class field %s to %s", from, to));
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof FieldInsnNode finsn && finsn.owner.equals(classNode.name)) {
                        finsn.name = renames.getOrDefault(finsn.name, finsn.name);
                    }
                }
            }
            return PatchResult.APPLY;
        }

        return PatchResult.PASS;
    }
}
