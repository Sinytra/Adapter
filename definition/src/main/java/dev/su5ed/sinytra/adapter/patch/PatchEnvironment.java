package dev.su5ed.sinytra.adapter.patch;

import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class PatchEnvironment {
    private static Function<String, String> referenceMapper;

    // Ugly stateful codec hack to allow us to remap srg -> moj when deserializing values
    public static String remapReference(String name) {
        return referenceMapper != null ? referenceMapper.apply(name) : name;
    }

    public static void setReferenceMapper(Function<String, String> matcherRemapper) {
        PatchEnvironment.referenceMapper = matcherRemapper;
    }

    private final Map<String, Map<String, String>> refmap;
    private final ClassLookup cleanClassLookup;
    private final MixinClassGenerator classGenerator;

    public PatchEnvironment(Map<String, Map<String, String>> refmap, ClassLookup cleanClassLookup) {
        this.refmap = refmap;
        this.cleanClassLookup = cleanClassLookup;
        this.classGenerator = new MixinClassGenerator();
    }

    public MixinClassGenerator getClassGenerator() {
        return this.classGenerator;
    }

    public ClassLookup getCleanClassLookup() {
        return this.cleanClassLookup;
    }

    public String remap(String cls, String reference) {
        String cleanReference = reference.replaceAll(" ", "");
        return Optional.ofNullable(this.refmap.get(cls))
            .map(map -> map.get(cleanReference))
            .orElse(reference);
    }
}
