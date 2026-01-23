package org.sinytra.adapter.next.flow;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ctx.MethodHelper;
import org.sinytra.adapter.next.env.ctx.RefMapper;
import org.sinytra.adapter.next.pipeline.config.*;
import org.sinytra.adapter.next.type.MixinType;
import org.sinytra.adapter.next.type.MixinTypes;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.AnnotationValueHandle;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.PatchEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MixinParser {

    @Nullable
    public static MixinClassHandle parseMixins(ClassNode classNode, PatchEnvironment environment) {
        ClassTarget cls = parseTargetClass(classNode);
        if (cls == null || cls.getTypes().size() > 1) return null;

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static MixinMethodHandle parseMixin(ClassTarget cls, MethodNode method, RefMapper mapper) {
        if (method.visibleAnnotations == null) return null;

        for (AnnotationNode annotation : method.visibleAnnotations) {
            AnnotationHandle handle = new AnnotationHandle(annotation);

            String internalName = Type.getType(annotation.desc).getInternalName();
            MixinType mixinType = MixinTypes.getMixinType(internalName);
            if (mixinType == null) continue;

            PropertyContainerTemplate template = mixinType.getConfigurationTemplate();
            Set<PropertyKey<?>> keys = template.getKeys();

            MutablePropertyContainer properties = MutablePropertyContainer.create(template);
            properties.setProperty(Keys.MIXIN_TYPE, annotation.desc);
            properties.setProperty(Keys.TARGET_CLASS, cls.getSingle().getInternalName());
            properties.setProperty(Keys.RETURN_TYPE, Type.getReturnType(method.desc));

            for (PropertyKey key : keys) {
                if (properties.hasProperty(key)) continue;

                Object value = handle.getValue(key.name()).map(AnnotationValueHandle::get).orElse(null);
                if (value == null) continue;

                if (key.parser() != null) {
                    Object parsed = key.parser().parse(value, mapper);
                    properties.setProperty(key, parsed);
                } else {
                    throw new IllegalStateException("Cannot parse for key %s, it does not define a parser".formatted(key.name()));
                }
            }

            // Set static property
            properties.setProperty(SpecialKeys.STATIC, MethodHelper.isStatic(method));

            return new MixinMethodHandle(mixinType, method, handle, properties);
        }

        return null;
    }

    @Nullable
    private static ClassTarget parseTargetClass(ClassNode classNode) {
        if (classNode.invisibleAnnotations == null) return null;

        for (AnnotationNode annotation : classNode.invisibleAnnotations) {
            if (annotation.desc.equals(MixinConstants.MIXIN)) {
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
