package dev.su5ed.sinytra.adapter.patch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationHandle;
import dev.su5ed.sinytra.adapter.patch.selector.AnnotationValueHandle;
import dev.su5ed.sinytra.adapter.patch.selector.FieldMatcher;
import dev.su5ed.sinytra.adapter.patch.selector.MethodContext;
import dev.su5ed.sinytra.adapter.patch.serialization.MethodTransformSerialization;
import dev.su5ed.sinytra.adapter.patch.transformer.RedirectAccessor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.gen.AccessorInfo;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class InterfacePatchInstance extends PatchInstance {
    public static final Collection<String> KNOWN_INTERFACE_MIXIN_TYPES = Set.of(Patch.ACCESSOR);

    public static final Codec<InterfacePatchInstance> CODEC = RecordCodecBuilder
        .<InterfacePatchInstance>create(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("targetClasses", List.of()).forGetter(p -> p.targetClasses),
            FieldMatcher.CODEC.listOf().optionalFieldOf("targetFields", List.of()).forGetter(p -> p.targetFields),
            Codec.STRING.listOf().optionalFieldOf("targetAnnotations", List.of()).forGetter(p -> p.targetAnnotations),
            MethodTransformSerialization.METHOD_TRANSFORM_CODEC.listOf().fieldOf("transforms").forGetter(p -> p.transforms)
        ).apply(instance, InterfacePatchInstance::new))
        .flatComapMap(Function.identity(), obj -> obj.targetAnnotationValues != null ? DataResult.error(() -> "Cannot serialize targetAnnotationValues") : DataResult.success(obj));

    private final List<FieldMatcher> targetFields;

    private InterfacePatchInstance(List<String> targetClasses, List<FieldMatcher> targetFields, List<String> targetAnnotations, List<MethodTransform> transforms) {
        this(targetClasses, targetFields, targetAnnotations, map -> true, List.of(), transforms);
    }

    private InterfacePatchInstance(List<String> targetClasses, List<FieldMatcher> targetFields, List<String> targetAnnotations, Predicate<Map<String, AnnotationValueHandle<?>>> targetAnnotationValues, List<ClassTransform> classTransforms, List<MethodTransform> transforms) {
        super(targetClasses, targetAnnotations, targetAnnotationValues, classTransforms, transforms);

        this.targetFields = targetFields;
    }

    @Override
    public Codec<? extends PatchInstance> codec() {
        return CODEC;
    }

    @Override
    public Result apply(ClassNode classNode, PatchEnvironment environment) {
        if ((classNode.access & Opcodes.ACC_INTERFACE) == 0) {
            return Result.PASS;
        }
        return super.apply(classNode, environment);
    }

    @Override
    protected boolean checkAnnotation(String owner, MethodNode method, AnnotationHandle methodAnnotation, PatchEnvironment environment, MethodContext.Builder builder) {
        if (KNOWN_INTERFACE_MIXIN_TYPES.contains(methodAnnotation.getDesc())) {
            // Find accessor target
            if (methodAnnotation.matchesDesc(Patch.ACCESSOR)) {
                FieldMatcher matcher = Optional.ofNullable(AccessorInfo.AccessorName.of(method.name))
                    .map(name -> environment.remap(owner, name.name))
                    .map(FieldMatcher::new)
                    .orElse(null);
                if (matcher != null && this.targetFields.stream().anyMatch(m -> m.matches(matcher))) {
                    builder.methodAnnotation(methodAnnotation);
                    return true;
                }
            }
        }
        return false;
    }

    public static class InterfaceClassPatchBuilderImpl extends BaseBuilder<InterfacePatchBuilder> implements InterfacePatchBuilder {
        private final Set<FieldMatcher> targetFields = new HashSet<>();

        @Override
        public InterfacePatchBuilder targetField(String... targets) {
            for (String target : targets) {
                this.targetFields.add(new FieldMatcher(target));
            }
            return this;
        }

        @Override
        public InterfacePatchBuilder modifyValue(String value) {
            return transform(new RedirectAccessor(value));
        }

        @Override
        public PatchInstance build() {
            return new InterfacePatchInstance(
                List.copyOf(this.targetClasses),
                List.copyOf(this.targetFields),
                List.copyOf(this.targetAnnotations),
                this.targetAnnotationValues,
                List.copyOf(this.classTransforms),
                List.copyOf(this.transforms)
            );
        }
    }
}
