package org.sinytra.adapter.patch.api;

import java.util.function.Function;

public final class GlobalReferenceMapper {
    private static Function<String, String> referenceMapper;

    // Ugly stateful codec hack to allow us to remap srg -> moj when deserializing values
    public static String remapReference(String name) {
        return referenceMapper != null ? referenceMapper.apply(name) : name;
    }

    public static void setReferenceMapper(Function<String, String> matcherRemapper) {
        referenceMapper = matcherRemapper;
    }

    private GlobalReferenceMapper() {}
}
