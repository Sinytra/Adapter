package org.sinytra.adapter.pipeline;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.mixin.MixinBridge;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_METHOD;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_AT;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_SLICE;

public final class MultiTargetSplitter {
    private static final MultiTargetSplitter INSTANCE = new MultiTargetSplitter();

    public static List<MixinParser.MixinMethodHandle> split(ClassNode classNode, ClassTarget classTarget, MixinParser.MixinMethodHandle mixin, PatchEnvironment environment) {
        if (mixin.properties().hasProperty(MixinKeys.TARGET_METHOD)) {
            return List.of(mixin);
        }
        String targetClass = mixin.properties().getProperty(ControlKeys.TARGET_CLASS).orElse(null);
        ClassNode clean = targetClass != null ? environment.cleanClassLookup().getClass(targetClass).orElse(null) : null;
        List<MethodNode> targets = clean != null ? findTargets(classNode, mixin.methodAnnotation(), clean, environment) : List.of();
        if (targets.size() < 2 || targets.stream().noneMatch(target -> needsPatching(classNode, mixin, targetClass, target, environment))) {
            return List.of(mixin);
        }

        MethodNode handler = mixin.methodNode();
        List<MethodNode> methods = new ArrayList<>();
        methods.add(handler);
        int position = classNode.methods.indexOf(handler);
        for (int i = 1; i < targets.size(); i++) {
            MethodNode copy = AdapterUtil.copyMethod(handler);
            String name = handler.name + "$adapter$target$" + i;
            for (int suffix = 1; AdapterUtil.findMethod(classNode, name, copy.desc) != null; suffix++) {
                name = handler.name + "$adapter$target$" + i + "$" + suffix;
            }
            copy.name = name;
            AnnotationHandle annotation = findAnnotation(copy, mixin.methodAnnotation().getDesc());
            annotation.setOrAppendNonNull(AT_METHOD, List.of(asSelector(targets.get(i))));
            annotation.setOrAppendNonNull("require", 0);
            classNode.methods.add(position + i, copy);
            methods.add(copy);
        }
        mixin.methodAnnotation().setOrAppendNonNull(AT_METHOD, List.of(asSelector(targets.getFirst())));
        environment.auditTrail().recordAudit(INSTANCE, classNode, "Split %s into one handler per target: %s", handler.name,
            targets.stream().map(MultiTargetSplitter::asSelector).toList());

        List<MixinParser.MixinMethodHandle> handles = new ArrayList<>();
        for (MethodNode method : methods) {
            MixinParser.MixinMethodHandle handle = MixinParser.parseMixin(classTarget, method, ref -> environment.refmapHolder().remap(classNode.name, ref), environment);
            if (handle != null) {
                handles.add(handle);
            }
        }
        return handles;
    }

    private static List<MethodNode> findTargets(ClassNode mixinClass, AnnotationHandle annotation, ClassNode clean, PatchEnvironment environment) {
        Object value = annotation.getValue(AT_METHOD).map(v -> v.get()).orElse(null);
        List<?> selectors = value instanceof List<?> list ? list : value != null ? List.of(value) : List.of();
        List<MethodNode> targets = new ArrayList<>();
        for (Object selector : selectors) {
            if (!(selector instanceof String string)) {
                return List.of();
            }
            MixinBridge.MemberSelector member = MixinBridge.parseMemberSelector(environment.refmapHolder().remap(mixinClass.name, string));
            if (member == null || member.field() || member.name() == null && member.desc() == null) {
                return List.of();
            }
            MethodQualifier qualifier = new MethodQualifier(null, member.name(), member.desc());
            int matches = 0;
            for (MethodNode method : clean.methods) {
                if (matches < member.maxMatches() && qualifier.matches(null, method.name, method.desc)) {
                    matches++;
                    if (!targets.contains(method)) {
                        targets.add(method);
                    }
                }
            }
        }
        return targets;
    }

    private static boolean needsPatching(ClassNode mixinClass, MixinParser.MixinMethodHandle mixin, String targetClass, MethodNode target, PatchEnvironment environment) {
        ClassNode dirty = environment.dirtyClassLookup().getClass(targetClass).orElse(null);
        MethodNode dirtyTarget = dirty != null ? AdapterUtil.findMethod(dirty, target.name, target.desc) : null;
        if (dirtyTarget == null) {
            return true;
        }
        AnnotationHandle annotation = mixin.methodAnnotation();
        AnnotationNode at = annotation.getNested(PROPERTY_AT).map(AnnotationHandle::unwrap).orElse(null);
        if (at == null) {
            return false;
        }
        AnnotationNode slice = annotation.getValue(PROPERTY_SLICE)
            .map(handle -> handle.get() instanceof List<?> list ? (list.isEmpty() ? null : (AnnotationNode) list.getFirst()) : (AnnotationNode) handle.get())
            .orElse(null);
        return MixinBridge.session(mixinClass.name, mixin.methodNode(), dirty, dirtyTarget, environment.refmapHolder())
            .findInjectionPoint(annotation.unwrap(), at, slice)
            .isEmpty();
    }

    @Nullable
    private static AnnotationHandle findAnnotation(MethodNode method, String desc) {
        return method.visibleAnnotations.stream()
            .filter(annotation -> annotation.desc.equals(desc))
            .findFirst()
            .map(AnnotationHandle::new)
            .orElse(null);
    }

    private static String asSelector(MethodNode method) {
        return method.name + method.desc;
    }

    private MultiTargetSplitter() {
    }
}
