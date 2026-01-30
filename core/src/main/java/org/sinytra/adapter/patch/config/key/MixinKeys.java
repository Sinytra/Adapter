package org.sinytra.adapter.patch.config.key;

import org.objectweb.asm.tree.AnnotationNode;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ann.ConstantData;
import org.sinytra.adapter.env.ann.SliceData;
import org.sinytra.adapter.patch.config.PropertyKey;
import org.sinytra.adapter.util.MethodQualifier;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

/**
 * Mixin Configuration Keys that directly map to annotation properties
 */
@SuppressWarnings("unchecked")
public class MixinKeys {
    public static final PropertyKey<MethodQualifier> TARGET_METHOD = PropertyKey.<MethodQualifier>builder("method")
        .parser((value, mapper) -> {
            // Get method targets
            String methodRef = ControlKeys.parseSingle(value, String.class);
            if (methodRef == null) {
                // We only support single method targets for now
                return null;
            }
            // Resolve method reference
            String reference = mapper.remap(methodRef);
            // Extract owner, name and desc using regex
            return MethodQualifier.parse(reference).orElse(null);
        })
        .serializer(q -> List.of(q.asDescriptor()))
        .build();
    public static final PropertyKey<AtData> TARGET_AT = PropertyKey.<AtData>builder("at")
        .parser((value, mapper) -> {
            AnnotationNode node = ControlKeys.parseSingle(value, AnnotationNode.class);
            if (node == null) return null;

            AnnotationHandle handle = new AnnotationHandle(node);
            return AtData.parse(handle, mapper).orElse(null);
        })
        .serializer(a -> List.of(a.toAnnotationNode())) // FIXME Sometimes a list, sometimes not. How to handle?
        .build();
    public static final PropertyKey<ConstantData> TARGET_CONSTANT = PropertyKey.<ConstantData>builder("constant")
        .parser((value, mapper) -> {
            AnnotationNode node = ControlKeys.parseSingle(value, AnnotationNode.class);
            AnnotationHandle handle = new AnnotationHandle(node);
            return ConstantData.parse(handle).orElse(null);
        })
        .serializer(c -> List.of(c.toAnnotationNode()))
        .build();
    // Mixin data
    public static final PropertyKey<Boolean> CANCELLABLE = PropertyKey.create("cancellable", Boolean.class);
    public static final PropertyKey<Integer> INDEX = PropertyKey.create("index", Integer.class);
    public static final PropertyKey<Integer> ORDINAL = PropertyKey.create("ordinal", Integer.class);
    public static final PropertyKey<Boolean> ARGS_ONLY = PropertyKey.create("argsOnly", Boolean.class);
    public static final PropertyKey<SliceData> SLICE = PropertyKey.<SliceData>builder("slice")
        .parser((value, mapper) -> SliceData.parse(new AnnotationHandle((AnnotationNode) value), mapper))
        .serializer(SliceData::toAnnotationNode)
        .build();
    public static final PropertyKey<List<SliceData>> SLICES = PropertyKey.<List<SliceData>>builder("slice")
        .parser((value, mapper) -> ((List<AnnotationNode>) value).stream()
            .map(AnnotationHandle::new)
            .map(n -> SliceData.parse(n, mapper))
            .toList())
        .serializer(l -> l.stream().map(SliceData::toAnnotationNode).toList())
        .build();
    public static final PropertyKey<LocalCapture> LOCALS = PropertyKey.create("locals", LocalCapture.class);
    public static final PropertyKey<Integer> REQUIRE = PropertyKey.create("require", Integer.class);
}
