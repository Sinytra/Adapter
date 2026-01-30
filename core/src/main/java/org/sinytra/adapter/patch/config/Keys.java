package org.sinytra.adapter.patch.config;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ann.ConstantData;
import org.sinytra.adapter.env.ann.SliceData;
import org.sinytra.adapter.env.param.MethodParameters;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.util.MethodQualifier;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@SuppressWarnings("unchecked")
public final class Keys {
    // Mixin meta
    public static final PropertyKey<String> MIXIN_TYPE = PropertyKey.create("mixin_type");
    public static final PropertyKey<String> TARGET_CLASS = PropertyKey.create("target_class");
    public static final PropertyKey<MethodQualifier> TARGET_METHOD = PropertyKey.<MethodQualifier>builder("method")
        .parser((value, mapper) -> {
            // Get method targets
            List<String> methodRefs = (List<String>) value;
            if (methodRefs.size() != 1) {
                // We only support single method targets for now
                return null;
            }
            // Resolve method reference
            String reference = mapper.remap(methodRefs.getFirst());
            // Extract owner, name and desc using regex
            return MethodQualifier.parse(reference).orElse(null);
        })
        .build();
    public static final PropertyKey<AtData> TARGET_AT = PropertyKey.<AtData>builder("at")
        .parser((value, mapper) -> {
            AnnotationNode node = parseSingle(value, AnnotationNode.class);
            if (node == null) return null;

            AnnotationHandle handle = new AnnotationHandle(node);
            return AtData.parse(handle, mapper).orElse(null);
        })
        .build();
    public static final PropertyKey<ConstantData> TARGET_CONSTANT = PropertyKey.<ConstantData>builder("constant")
        .parser((value, mapper) -> {
            AnnotationNode node = parseSingle(value, AnnotationNode.class);
            AnnotationHandle handle = new AnnotationHandle(node);
            return ConstantData.parse(handle).orElse(null);
        })
        .build();
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
    public static final PropertyKey<LocalCapture> LOCALS = PropertyKey.create("locals", LocalCapture.class);
    public static final PropertyKey<Integer> REQUIRE = PropertyKey.create("require", Integer.class);

    private Keys() {
    }

    @SuppressWarnings("rawtypes")
    private static <T> T parseSingle(Object obj, Class<T> cls) {
        if (obj instanceof List list) {
            return list.size() == 1 ? (T) list.getFirst() : null;
        }
        return cls.isInstance(obj) ? (T) obj : null;
    }
}
