package org.sinytra.adapter.patch.transformer.dynamic;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicAnonymousShadowFieldTypePatch implements ClassTransform {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public Patch.Result apply(ClassNode classNode, @Nullable AnnotationValueHandle<?> annotation, PatchContext context) {
        if (annotation == null || !annotation.getKey().equals("targets")) {
            return Patch.Result.PASS;
        }
        List<String> targets = (List<String>) annotation.get();
        if (targets.size() != 1) {
            return Patch.Result.PASS;
        }

        String targetReference = context.remap(targets.get(0));
        if (!AdapterUtil.isAnonymousClass(targetReference)) {
            return Patch.Result.PASS;
        }
        ClassNode targetClass = context.environment().dirtyClassLookup().getClass(targetReference).orElse(null);
        if (targetClass == null) {
            return Patch.Result.PASS;
        }

        Map<String, String> renames = new HashMap<>();
        Multimap<String, FieldNode> fields = HashMultimap.create();
        for (FieldNode targetField : targetClass.fields) {
            fields.put(targetField.desc, targetField);
        }

        for (FieldNode field : classNode.fields) {
            if (field.visibleAnnotations != null) {
                for (AnnotationNode ann : field.visibleAnnotations) {
                    if (MixinConstants.SHADOW.equals(ann.desc)) {
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
            renames.forEach((from, to) -> LOGGER.info("Renaming anonymous class field {}.{} to {}", classNode.name, from, to));
            for (MethodNode method : classNode.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof FieldInsnNode finsn && finsn.owner.equals(classNode.name)) {
                        finsn.name = renames.getOrDefault(finsn.name, finsn.name);
                    }
                }
            }
            return Patch.Result.APPLY;
        }

        return Patch.Result.PASS;
    }
}
