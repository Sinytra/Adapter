package dev.su5ed.sinytra.adapter.patch;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PatchEnvironment {
    private static Function<String, String> matcherRemapper;

    // Ugly stateful codec hack to allow us to remap srg -> moj when deserializing values
    public static String remapMethodName(String name) {
        return matcherRemapper != null ? matcherRemapper.apply(name) : name;
    }

    public static void setMatcherRemapper(Function<String, String> matcherRemapper) {
        PatchEnvironment.matcherRemapper = matcherRemapper;
    }

    private final Map<String, Map<String, String>> refmap;

    public PatchEnvironment(Map<String, Map<String, String>> refmap) {
        this.refmap = refmap;
    }

    public String remap(String cls, String reference) {
        return Optional.ofNullable(this.refmap.get(cls))
            .map(map -> map.get(reference))
            .orElse(reference);
    }
}
