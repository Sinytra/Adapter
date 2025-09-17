package org.sinytra.adapter.patch.fixes;

import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BytecodeFixerUpper {
    public static final List<TypeAdapterProvider> DEFAULT_PROVIDERS = List.of(
        SupplierTypeAdapter.INSTANCE,
        ObjectTypeAdapter.INSTANCE
    );

    private final List<TypeAdapter> fieldTypeAdapters;
    private final List<TypeAdapterProvider> dynamicTypeAdapters;
    private final BytecodeFixerJarGenerator generator;
    private final ClassLookup cleanLookup;
    private final ClassLookup dirtyLookup;

    private final Map<String, Pair<Type, Type>> fieldTypeChangesCache = new ConcurrentHashMap<>();

    public BytecodeFixerUpper(ClassLookup cleanLookup, ClassLookup dirtyLookup, List<TypeAdapter> fieldTypeAdapters) {
        this(cleanLookup, dirtyLookup, fieldTypeAdapters, DEFAULT_PROVIDERS);
    }

    public BytecodeFixerUpper(ClassLookup cleanLookup, ClassLookup dirtyLookup, List<TypeAdapter> fieldTypeAdapters, List<TypeAdapterProvider> dynamicTypeAdapters) {
        this.cleanLookup = cleanLookup;
        this.dirtyLookup = dirtyLookup;
        this.fieldTypeAdapters = fieldTypeAdapters;
        this.dynamicTypeAdapters = dynamicTypeAdapters;
        this.generator = new BytecodeFixerJarGenerator();
    }

    public BytecodeFixerJarGenerator getGenerator() {
        return this.generator;
    }

    public Pair<Type, Type> getFieldTypeChange(String owner, String name) {
        String key = owner + ":" + name;
        return fieldTypeChangesCache.computeIfAbsent(key, k -> {
            FieldNode cleanField = this.cleanLookup.findField(owner, name).orElse(null);
            if (cleanField == null) {
                return null;
            }
            FieldNode dirtyField = this.dirtyLookup.findField(owner, name).orElse(null);
            if (dirtyField == null) {
                return null;
            }
            if (!cleanField.desc.equals(dirtyField.desc)) {
                return Pair.of(Type.getType(cleanField.desc), Type.getType(dirtyField.desc));
            }
            return null;
        });
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
