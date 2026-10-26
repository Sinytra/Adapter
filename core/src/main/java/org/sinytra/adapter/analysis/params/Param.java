package org.sinytra.adapter.analysis.params;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.List;

public record Param(Type type, @Nullable String name) {
    public static List<Param> of(List<Type> types) {
        return types.stream().map(type -> new Param(type, null)).toList();
    }
}
