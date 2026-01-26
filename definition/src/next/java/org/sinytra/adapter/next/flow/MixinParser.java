package org.sinytra.adapter.next.flow;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ctx.MethodHelper;
import org.sinytra.adapter.next.env.ctx.RefMapper;
import org.sinytra.adapter.next.env.util.MixinAnnotations;
import org.sinytra.adapter.next.pipeline.config.*;
import org.sinytra.adapter.next.mixin.MixinType;
import org.sinytra.adapter.next.mixin.MixinTypes;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.next.env.ctx.PatchEnvironment;

import java.util.ArrayList;
import java.util.List;

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

    private static MixinMethodHandle parseMixin(ClassTarget cls, MethodNode method, RefMapper mapper) {
        if (method.visibleAnnotations == null) return null;

        for (AnnotationNode annotation : method.visibleAnnotations) {
            AnnotationHandle handle = new AnnotationHandle(annotation);

            String internalName = Type.getType(annotation.desc).getInternalName();
            MixinType mixinType = MixinTypes.getMixinType(internalName);
            if (mixinType == null) continue;

            PropertyContainerTemplate template = mixinType.getConfigurationTemplate();

            MutablePropertyContainer properties = MutablePropertyContainer.parse(handle, template, mapper);
            properties.setProperty(Keys.MIXIN_TYPE, annotation.desc);
            properties.setProperty(Keys.TARGET_CLASS, cls.getSingle().getInternalName());
            properties.setProperty(Keys.RETURN_TYPE, Type.getReturnType(method.desc));
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
