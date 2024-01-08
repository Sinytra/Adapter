package dev.su5ed.sinytra.adapter.patch.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.api.GlobalReferenceMapper;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Map;

public final class BytecodeFixerUpper {
    public static final List<TypeAdapterProvider> DEFAULT_PROVIDERS = List.of(
        SupplierTypeAdapter.INSTANCE
    );

    private final Map<String, Map<String, Pair<Type, Type>>> newFieldTypes;
    private final List<TypeAdapter> fieldTypeAdapters;
    private final List<TypeAdapterProvider> dynamicTypeAdapters;
    private final BytecodeFixerJarGenerator generator;

    public BytecodeFixerUpper(Map<String, Map<String, Pair<Type, Type>>> newFieldTypes, List<TypeAdapter> fieldTypeAdapters) {
        this(newFieldTypes, fieldTypeAdapters, DEFAULT_PROVIDERS);
    }

    public BytecodeFixerUpper(Map<String, Map<String, Pair<Type, Type>>> newFieldTypes, List<TypeAdapter> fieldTypeAdapters, List<TypeAdapterProvider> dynamicTypeAdapters) {
        // Remap field names
        ImmutableMap.Builder<String, Map<String, Pair<Type, Type>>> builder = ImmutableMap.builder();
        newFieldTypes.forEach((owner, fields) -> {
            ImmutableMap.Builder<String, Pair<Type, Type>> fieldsBuilder = ImmutableMap.builder();
            fields.forEach((k, v) -> fieldsBuilder.put(GlobalReferenceMapper.remapReference(k), v));
            builder.put(owner, fieldsBuilder.build());
        });
        this.newFieldTypes = builder.build();
        this.fieldTypeAdapters = fieldTypeAdapters;
        this.dynamicTypeAdapters = dynamicTypeAdapters;
        this.generator = new BytecodeFixerJarGenerator();
    }

    public BytecodeFixerJarGenerator getGenerator() {
        return this.generator;
    }

    public Pair<Type, Type> getFieldTypeChange(String owner, String name) {
        Map<String, Pair<Type, Type>> fields = this.newFieldTypes.get(owner);
        return fields != null ? fields.get(name) : null;
    }

    @Nullable
    public TypeAdapter getTypeAdapter(Type from, Type to) {
        for (TypeAdapter typeAdapter : this.fieldTypeAdapters) {
            if (typeAdapter.from().equals(from) && typeAdapter.to().equals(to)) {
                return typeAdapter;
            }
        }
        for (TypeAdapterProvider dynamicAdapter : this.dynamicTypeAdapters) {
            TypeAdapter adapter = dynamicAdapter.provide(from, to);
            if (adapter != null) {
                return adapter;
            }
        }
        return null;
    }
}
