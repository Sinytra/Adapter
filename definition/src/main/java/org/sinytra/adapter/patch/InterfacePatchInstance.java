package org.sinytra.adapter.patch;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.analysis.selector.FieldMatcher;
import org.sinytra.adapter.patch.api.ClassTransform;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.api.MixinConstants;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.util.AdapterUtil;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class InterfacePatchInstance extends PatchInstance {
    public static final Collection<String> KNOWN_INTERFACE_MIXIN_TYPES = Set.of(MixinConstants.ACCESSOR);

    private final List<FieldMatcher> targetFields;

    private InterfacePatchInstance(List<String> targetClasses, List<FieldMatcher> targetFields, List<String> targetAnnotations, Predicate<AnnotationHandle> targetAnnotationValues, List<ClassTransform> classTransforms, List<MethodTransform> transforms) {
        super(targetClasses, targetAnnotations, targetAnnotationValues, classTransforms, transforms);

        this.targetFields = targetFields;
    }

    @Override
    public Result apply(ClassNode classNode, PatchEnvironment environment) {
        if ((classNode.access & Opcodes.ACC_INTERFACE) == 0) {
            return Result.PASS;
        }
        return super.apply(classNode, environment);
    }

    @Override
    protected boolean checkAnnotation(String owner, MethodNode method, AnnotationHandle methodAnnotation, PatchEnvironment environment, MethodContextImpl.Builder builder) {
        if (KNOWN_INTERFACE_MIXIN_TYPES.contains(methodAnnotation.getDesc())) {
            // Find accessor target
            if (methodAnnotation.matchesDesc(MixinConstants.ACCESSOR)) {
                FieldMatcher matcher = AdapterUtil.getAccessorTargetFieldName(owner, method, methodAnnotation, environment)
                    .map(FieldMatcher::new)
                    .orElse(null);
                if (matcher != null && (this.targetFields.isEmpty() || this.targetFields.stream().anyMatch(m -> m.matches(matcher)))) {
                    builder.methodNode(method);
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
