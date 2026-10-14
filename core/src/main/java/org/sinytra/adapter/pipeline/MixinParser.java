package org.sinytra.adapter.pipeline;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.api.ClassLookup;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.model.ann.RefMapper;
import org.sinytra.adapter.model.config.MutablePropertyContainer;
import org.sinytra.adapter.model.config.PropertyContainer;
import org.sinytra.adapter.model.config.PropertyContainerTemplate;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.pipeline.injector.MixinType;
import org.sinytra.adapter.pipeline.injector.MixinTypes;

import java.util.ArrayList;
import java.util.List;

public class MixinParser {

    @Nullable
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

    @Nullable
    public static MixinMethodHandle parseMixin(ClassTarget cls, MethodNode method, RefMapper mapper, PatchEnvironment environment) {
        if (method.visibleAnnotations == null || cls.getTypes().isEmpty()) return null;

        for (AnnotationNode annotation : method.visibleAnnotations) {
            AnnotationHandle handle = new AnnotationHandle(annotation);

            String internalName = Type.getType(annotation.desc).getInternalName();
            MixinType mixinType = MixinTypes.getMixinType(internalName);
            if (mixinType == null) continue;

            PropertyContainerTemplate template = mixinType.getConfigurationTemplate();

            MutablePropertyContainer properties = MutablePropertyContainer.parse(handle, template, mapper);
            properties.setProperty(ControlKeys.MIXIN_TYPE, annotation.desc);
            String targetClass = cls.getTypes().getFirst().getInternalName();
            properties.setProperty(ControlKeys.TARGET_CLASS, targetClass);
            properties.setProperty(ControlKeys.RETURN_TYPE, Type.getReturnType(method.desc));
            properties.setProperty(ControlKeys.STATIC, MethodAnalyzer.isStatic(method));
            resolveTargetDescriptor(properties, environment);

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

    private static void resolveTargetDescriptor(MutablePropertyContainer properties, PatchEnvironment environment) {
        MethodQualifier target = properties.getProperty(MixinKeys.TARGET_METHOD).orElse(null);
        String targetClass = properties.getProperty(ControlKeys.TARGET_CLASS).orElse(null);
        if (target == null || target.desc() != null || target.name() == null || targetClass == null) {
            return;
        }

        List<MethodNode> cleanCandidates = findMethodsByName(environment.cleanClassLookup(), targetClass, target.name());
        List<MethodNode> dirtyCandidates = findMethodsByName(environment.dirtyClassLookup(), targetClass, target.name());
        if (cleanCandidates.size() == 1 && dirtyCandidates.size() <= 1) {
            MethodNode method = cleanCandidates.getFirst();
            properties.setProperty(MixinKeys.TARGET_METHOD, new MethodQualifier(target.owner(), method.name, method.desc));
        }
    }

    private static List<MethodNode> findMethodsByName(ClassLookup lookup, String owner, String name) {
        return lookup.getClass(owner)
            .map(cls -> cls.methods.stream().filter(m -> m.name.equals(name)).toList())
            .orElse(List.of());
    }

    private static RefMapper mapperFor(ClassNode classNode, PatchEnvironment environment) {
        return ref -> environment.refmapHolder().remap(classNode.name, ref);
    }

    public record MixinMethodHandle(MixinType mixinType, MethodNode methodNode, AnnotationHandle methodAnnotation, PropertyContainer properties) {
    }

    public record MixinClassHandle(ClassTarget classTarget, List<MixinMethodHandle> mixins) {
    }
}
