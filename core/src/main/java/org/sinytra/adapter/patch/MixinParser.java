package org.sinytra.adapter.patch;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.RefMapper;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.config.*;
import org.sinytra.adapter.patch.config.key.ControlKeys;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.mixin.MixinType;
import org.sinytra.adapter.patch.mixin.MixinTypes;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ctx.PatchEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MixinParser {

    @Nullable
    public static MixinClassHandle parseMixins(ClassNode classNode, PatchEnvironment environment) {
        ClassTarget cls = parseTargetClass(classNode);
        if (cls == null) return null;

        RefMapper refMapper = ref -> environment.refmapHolder().remap(classNode.name, ref);
        List<MixinMethodHandle> methods = new ArrayList<>();
        for (MethodNode method : classNode.methods) {
            MixinMethodHandle handle = parseMixin(cls, method, refMapper);
            if (handle != null) {
                methods.add(handle);
            }
        }

        return new MixinClassHandle(cls, methods);
    }

    public static MixinMethodHandle parseMixin(ClassTarget cls, MethodNode method, RefMapper mapper) {
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

            return new MixinMethodHandle(mixinType, method, handle, properties);
        }

        return null;
    }

    @Nullable
    private static ClassTarget parseTargetClass(ClassNode classNode) {
        if (classNode.invisibleAnnotations == null) return null;

        for (AnnotationNode annotation : classNode.invisibleAnnotations) {
            if (annotation.desc.equals(MixinAnnotations.MIXIN)) {
                AnnotationHandle ann = new AnnotationHandle(annotation);
                return ClassTarget.parse(ann);
            }
        }

        return null;
    }

    public record MixinMethodHandle(MixinType mixinType, MethodNode methodNode, AnnotationHandle methodAnnotation, PropertyContainer properties) {
    }

    public record MixinClassHandle(ClassTarget classTarget, List<MixinMethodHandle> mixins) {
    }
}
