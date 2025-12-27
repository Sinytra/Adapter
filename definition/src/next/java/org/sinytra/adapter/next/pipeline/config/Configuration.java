package org.sinytra.adapter.next.pipeline.config;

import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.SliceData;
import org.sinytra.adapter.next.env.param.MethodParameters;
import org.sinytra.adapter.patch.util.MethodQualifier;

public interface Configuration extends PropertyContainer {
    String getMixinType();

    String getTargetClass();

    MethodQualifier getTargetMethod();

    AtData getAtData();

    MethodParameters getParameters();

    Type getReturnType();

    boolean shouldDelete();

    MutableConfiguration subConfig();

    MutableConfiguration copy();

    final class Keys {
        // Mixin meta
        public static final PropertyKey<String> MIXIN_TYPE = new PropertyKey<>("mixin_type");
        public static final PropertyKey<String> TARGET_CLASS = new PropertyKey<>("target_class");
        public static final PropertyKey<MethodQualifier> TARGET_METHOD = new PropertyKey<>("target_method");
        public static final PropertyKey<AtData> TARGET_AT = new PropertyKey<>("target_at");
        // Method
        public static final PropertyKey<MethodParameters> PARAMETERS = new PropertyKey<>("parameters");
        public static final PropertyKey<Type> RETURN_TYPE = new PropertyKey<>("return_type");
        // Control
        public static final PropertyKey<Boolean> DELETE = new PropertyKey<>("delete");
        // Misc
        public static final PropertyKey<Integer> ORDINAL = new PropertyKey<>("ordinal");
        public static final PropertyKey<Integer> INDEX = new PropertyKey<>("index");
        public static final PropertyKey<SliceData> SLICE = new PropertyKey<>("slice");

        private Keys() {
        }
    }
}
