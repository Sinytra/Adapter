package org.sinytra.adapter.pipeline.transform;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.api.ClassLookup;
import org.sinytra.adapter.api.ClassPatchContext;
import org.sinytra.adapter.api.ClassTransformer;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.pipeline.MixinParser;
import org.sinytra.adapter.pipeline.ctx.PatchContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AnonymousTargetTransformer implements ClassTransformer {
    @Override
    public PatchResult apply(ClassNode classNode, ClassPatchContext context) {
        Type target = context.singleTarget();
        if (target == null || !AdapterUtil.isAnonymousClass(target.getInternalName())) {
            return PatchResult.PASS;
        }

        PatchResult result = PatchResult.PASS;
        Type renumbered = findRenumbered(context.environment(), target.getInternalName());
        if (renumbered != null && context instanceof PatchContext patchContext && patchContext.classTarget() != null) {
            retarget(classNode, patchContext, target, renumbered);
            target = renumbered;
            result = PatchResult.APPLY;
        }

        ClassNode targetClass = context.environment().dirtyClassLookup().getClass(target.getInternalName()).orElse(null);
        if (targetClass != null && renameShadowFields(classNode, targetClass, context)) {
            result = PatchResult.APPLY;
        }
        return result;
    }

    @Nullable
    private static Type findRenumbered(PatchEnvironment environment, String target) {
        ClassNode cleanClass = environment.cleanClassLookup().getClass(target).orElse(null);
        if (cleanClass == null || cleanClass.outerClass == null || cleanClass.outerMethod == null) {
            return null;
        }
        List<String> cleanClasses = findAnonymousClasses(environment.cleanClassLookup(), cleanClass);
        List<String> dirtyClasses = findAnonymousClasses(environment.dirtyClassLookup(), cleanClass);
        int index = cleanClasses.indexOf(target);
        if (index == -1 || cleanClasses.size() != dirtyClasses.size() || dirtyClasses.get(index).equals(target)) {
            return null;
        }
        return Type.getObjectType(dirtyClasses.get(index));
    }

    private static List<String> findAnonymousClasses(ClassLookup lookup, ClassNode anonymousClass) {
        ClassNode outerClass = lookup.getClass(anonymousClass.outerClass).orElse(null);
        if (outerClass == null) {
            return List.of();
        }
        List<String> classes = new ArrayList<>();
        for (InnerClassNode innerClass : outerClass.innerClasses) {
            if (AdapterUtil.isAnonymousClass(innerClass.name)) {
                lookup.getClass(innerClass.name)
                    .filter(inner -> anonymousClass.outerClass.equals(inner.outerClass) && anonymousClass.outerMethod.equals(inner.outerMethod)
                        && anonymousClass.outerMethodDesc.equals(inner.outerMethodDesc))
                    .ifPresent(inner -> classes.add(inner.name));
            }
        }
        classes.sort(Comparator.comparingInt(name -> Integer.parseInt(name.substring(name.lastIndexOf('$') + 1))));
        return classes;
    }

    private void retarget(ClassNode classNode, PatchContext context, Type from, Type to) {
        ClassTarget classTarget = context.classTarget();
        classTarget.set(to);
        for (MethodNode method : classNode.methods) {
            MixinParser.MixinMethodHandle handle = MixinParser.parseMixin(classTarget, method, context, context.environment());
            if (handle == null) continue;

            handle.properties().getProperty(MixinKeys.TARGET_METHOD)
                .filter(q -> from.getDescriptor().equals(q.owner()))
                .map(q -> handle.properties().serialize(MixinKeys.TARGET_METHOD, q.withOwner(to)))
                .ifPresent(q -> handle.methodAnnotation().setOrAppendNonNull(MixinKeys.TARGET_METHOD.name(), q));
        }
        context.environment().auditTrail().recordAudit(this, classNode, "Retarget anonymous class %s to %s", from.getInternalName(), to.getInternalName());
    }

    private boolean renameShadowFields(ClassNode classNode, ClassNode targetClass, ClassPatchContext context) {
        Map<String, List<FieldNode>> targetFields = targetClass.fields.stream().collect(Collectors.groupingBy(field -> field.desc));
        Map<String, String> renames = new HashMap<>();
        for (FieldNode field : classNode.fields) {
            List<FieldNode> candidates = targetFields.getOrDefault(field.desc, List.of());
            if (AdapterUtil.isShadowField(field) && candidates.size() == 1 && !candidates.getFirst().name.equals(field.name)) {
                renames.put(field.name, candidates.getFirst().name);
                field.name = candidates.getFirst().name;
            }
        }
        if (renames.isEmpty()) {
            return false;
        }

        renames.forEach((from, to) -> context.environment().auditTrail().recordAudit(this, classNode, "Rename anonymous class field %s to %s", from, to));
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof FieldInsnNode fieldInsn && fieldInsn.owner.equals(classNode.name)) {
                    fieldInsn.name = renames.getOrDefault(fieldInsn.name, fieldInsn.name);
                }
            }
        }
        return true;
    }
}
