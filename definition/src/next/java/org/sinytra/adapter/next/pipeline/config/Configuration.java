package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.ConstantData;
import org.sinytra.adapter.next.env.ann.SliceData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.List;

public interface Configuration extends PropertyContainer {
    String getMixinType();

    String getTargetClass();

    MethodQualifier getTargetMethod();

    AtData getAtData();

    MethodParameters getParameters();

    Type getReturnType();

    boolean shouldDelete();

    MutableConfiguration subConfig();

    MutableConfiguration subConfig(PropertyContainerTemplate template);

    MutableConfiguration copy();

    final class Keys {
        // Mixin meta
        public static final PropertyKey<String> MIXIN_TYPE = PropertyKey.create("mixin_type");
        public static final PropertyKey<String> TARGET_CLASS = PropertyKey.create("target_class");
        public static final PropertyKey<MethodQualifier> TARGET_METHOD = PropertyKey.create("target_method");
        public static final PropertyKey<AtData> TARGET_AT = PropertyKey.create("target_at");
        public static final PropertyKey<ConstantData> TARGET_CONSTANT = PropertyKey.create("target_constant");
        // Method
        public static final PropertyKey<MethodParameters> PARAMETERS = PropertyKey.create("parameters");
        public static final PropertyKey<Type> RETURN_TYPE = PropertyKey.create("return_type");
        // Control
        public static final PropertyKey<Boolean> DELETE = PropertyKey.create("delete");

        // Mixin data
        public static final PropertyKey<Boolean> CANCELLABLE = PropertyKey.create("cancellable", Boolean.class);
        public static final PropertyKey<Integer> INDEX = PropertyKey.create("index", Integer.class);
        public static final PropertyKey<Integer> ORDINAL = PropertyKey.create("ordinal", Integer.class);
        public static final PropertyKey<Boolean> ARGS_ONLY = PropertyKey.create("argsOnly", Boolean.class);
        public static final PropertyKey<SliceData> SLICE = PropertyKey.<SliceData>builder("slice")
            .parser((value, mapper) -> SliceData.parse(new AnnotationHandle((AnnotationNode) value), mapper))
            .build();
        public static final PropertyKey<List<SliceData>> SLICES = PropertyKey.<List<SliceData>>builder("slice")
            .parser((value, mapper) -> ((List<AnnotationNode>) value).stream()
                .map(AnnotationHandle::new)
                .map(n -> SliceData.parse(n, mapper))
                .toList())
            .build();

        private Keys() {
        }
    }

    final class SpecialKeys {
        // Hidden
        public static final PropertyKey<MethodInsnNode> EXTRACT_TARGET = PropertyKey.create("_extract_target_minsn");

        private SpecialKeys() {
        }
    }
}
