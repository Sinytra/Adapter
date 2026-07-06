package org.sinytra.adapter.patch;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.RefMapper;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.config.MutablePropertyContainer;
import org.sinytra.adapter.patch.config.PropertyContainer;
import org.sinytra.adapter.patch.config.PropertyContainerTemplate;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.mixin.MixinType;
import org.sinytra.adapter.patch.mixin.MixinTypes;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MixinParser {

    public static ClassTarget prepareMixinClass(ClassNode classNode, PatchEnvironment environment) {
        RefMapper mapper = mapperFor(classNode, environment);
        return parseTargetClass(classNode, mapper);
    }

    @Nullable
    public static MixinClassHandle parseMixins(ClassTarget cls, ClassNode classNode, PatchEnvironment environment) {
        RefMapper mapper = mapperFor(classNode, environment);

        List<MixinMethodHandle> methods = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            MixinMethodHandle handle = parseMixin(cls, method, mapper, environment);
            if (handle != null) {
                methods.add(handle);
            }
        }

        return new MixinClassHandle(cls, methods);
    }

    public static MixinMethodHandle parseMixin(ClassTarget cls, MethodNode method, RefMapper mapper, PatchEnvironment environment) {
        if (method.visibleAnnotations == null) return null;

        for (AnnotationNode annotation : method.visibleAnnotations) {
            AnnotationHandle handle = new AnnotationHandle(annotation);

            String internalName = Type.getType(annotation.desc).getInternalName();
            MixinType mixinType = MixinTypes.getMixinType(internalName);
            if (mixinType == null) continue;

            PropertyContainerTemplate template = mixinType.getConfigurationTemplate();

            MutablePropertyContainer properties = MutablePropertyContainer.parse(handle, template, mapper);
            properties.setProperty(ControlKeys.MIXIN_TYPE, annotation.desc);
            String targetClass = Objects.requireNonNullElseGet(cls.getSingle(), () -> cls.getTypes().getFirst()).getInternalName();
            properties.setProperty(ControlKeys.TARGET_CLASS, targetClass);
            properties.setProperty(ControlKeys.RETURN_TYPE, Type.getReturnType(method.desc));
            properties.setProperty(SpecialKeys.STATIC, MethodHelper.isStatic(method));
            if (!postProcessMixin(properties, environment)) {
                continue;
            }

            return new MixinMethodHandle(mixinType, method, handle, properties);
        }

        return null;
    }

    @Nullable
    private static ClassTarget parseTargetClass(ClassNode classNode, RefMapper mapper) {
        if (classNode.invisibleAnnotations == null) return null;

        for (AnnotationNode annotation : classNode.invisibleAnnotations) {
            if (annotation.desc.equals(MixinAnnotations.MIXIN)) {
                AnnotationHandle ann = new AnnotationHandle(annotation);
                return ClassTarget.parse(ann, mapper);
            }
        }

        return null;
    }

    private static boolean postProcessMixin(MutablePropertyContainer properties, PatchEnvironment environment) {
        MethodQualifier target = properties.getProperty(MixinKeys.TARGET_METHOD).orElse(null);
        if (target != null && target.desc() == null) {
            ClassNode dirtyTarget = properties.getProperty(ControlKeys.TARGET_CLASS)
                .flatMap(cls -> environment.dirtyClassLookup().getClass(cls))
                .orElse(null);
            if (dirtyTarget == null) {
                return false;
            }

            List<MethodNode> targets = dirtyTarget.methods.stream()
                .filter(m -> target.matches(MethodQualifier.create(m)))
                .toList();
            if (targets.size() == 1) {
                properties.setProperty(MixinKeys.TARGET_METHOD, MethodQualifier.create(targets.getFirst()));
            }
        }
        return true;
    }

    private static RefMapper mapperFor(ClassNode classNode, PatchEnvironment environment) {
        return ref -> environment.refmapHolder().remap(classNode.name, ref);
    }

    public record MixinMethodHandle(MixinType mixinType, MethodNode methodNode, AnnotationHandle methodAnnotation, PropertyContainer properties) {
    }

    public record MixinClassHandle(ClassTarget classTarget, List<MixinMethodHandle> mixins) {
    }
}
