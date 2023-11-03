package dev.su5ed.sinytra.adapter.patch;

import dev.su5ed.sinytra.adapter.patch.analysis.InheritanceHandler;
import dev.su5ed.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;
import org.jetbrains.annotations.Nullable;

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
    @Nullable
    private final BytecodeFixerUpper bytecodeFixerUpper;
    private final MixinClassGenerator classGenerator;
    private final InheritanceHandler inheritanceHandler;

    public PatchEnvironment(Map<String, Map<String, String>> refmap, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper) {
        this.refmap = refmap;
        this.cleanClassLookup = cleanClassLookup;
        this.bytecodeFixerUpper = bytecodeFixerUpper;
        this.classGenerator = new MixinClassGenerator();
        this.inheritanceHandler = new InheritanceHandler(AdapterUtil::maybeGetClassNode);
    }

    public MixinClassGenerator getClassGenerator() {
        return this.classGenerator;
    }

    public ClassLookup getCleanClassLookup() {
        return this.cleanClassLookup;
    }

    @Nullable
    public BytecodeFixerUpper getBytecodeFixerUpper() {
        return this.bytecodeFixerUpper;
    }

    public InheritanceHandler getInheritanceHandler() {
        return this.inheritanceHandler;
    }

    public String remap(String cls, String reference) {
        String cleanReference = reference.replaceAll(" ", "");
        return Optional.ofNullable(this.refmap.get(cls))
            .map(map -> map.get(cleanReference))
            .orElse(reference);
    }
}
