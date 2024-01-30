package dev.su5ed.sinytra.adapter.patch;

import dev.su5ed.sinytra.adapter.patch.analysis.InheritanceHandler;
import dev.su5ed.sinytra.adapter.patch.api.MixinClassGenerator;
import dev.su5ed.sinytra.adapter.patch.api.PatchEnvironment;
import dev.su5ed.sinytra.adapter.patch.api.RefmapHolder;
import dev.su5ed.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;
import org.jetbrains.annotations.Nullable;

public record PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper,
                                   MixinClassGenerator classGenerator, InheritanceHandler inheritanceHandler, int fabricLVTCompatibility) implements PatchEnvironment {

    public PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility) {
        this(refmapHolder, cleanClassLookup, bytecodeFixerUpper, new MixinClassGeneratorImpl(), new InheritanceHandler(AdapterUtil::maybeGetClassNode), fabricLVTCompatibility);
    }
}
