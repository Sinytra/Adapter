package dev.su5ed.sinytra.adapter.patch.fixes;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.PatchEnvironment;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.List;
import java.util.Map;

public final class BytecodeFixerUpper {
    private final Map<String, Map<String, Pair<Type, Type>>> newFieldTypes;
    private final List<FieldTypeFix> fieldTypeAdapters;
    private final BytecodeFixerJarGenerator generator;

    public BytecodeFixerUpper(Map<String, Map<String, Pair<Type, Type>>> newFieldTypes, List<FieldTypeFix> fieldTypeAdapters) {
        // Remap field names
        ImmutableMap.Builder<String, Map<String, Pair<Type, Type>>> builder = ImmutableMap.builder();
        newFieldTypes.forEach((owner, fields) -> {
            ImmutableMap.Builder<String, Pair<Type, Type>> fieldsBuilder = ImmutableMap.builder();
            fields.forEach((k, v) -> fieldsBuilder.put(PatchEnvironment.remapReference(k), v));
            builder.put(owner, fieldsBuilder.build());
        });
        this.newFieldTypes = builder.build();
        this.fieldTypeAdapters = fieldTypeAdapters;
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
    public FieldTypeFix getFieldTypeAdapter(Type from, Type to) {
        for (FieldTypeFix typeAdapter : this.fieldTypeAdapters) {
            if (typeAdapter.from().equals(from) && typeAdapter.to().equals(to)) {
                return typeAdapter;
            }
        }
        return null;
    }
}
