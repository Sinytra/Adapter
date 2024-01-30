package dev.su5ed.sinytra.adapter.patch.api;

import dev.su5ed.sinytra.adapter.patch.PatchEnvironmentImpl;
import dev.su5ed.sinytra.adapter.patch.analysis.InheritanceHandler;
import dev.su5ed.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;
import org.jetbrains.annotations.Nullable;

public interface PatchEnvironment {
    static PatchEnvironment create(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility) {
        return new PatchEnvironmentImpl(refmapHolder, cleanClassLookup, bytecodeFixerUpper, fabricLVTCompatibility);
    }

    MixinClassGenerator classGenerator();

    ClassLookup cleanClassLookup();

    @Nullable
    BytecodeFixerUpper bytecodeFixerUpper();

    InheritanceHandler inheritanceHandler();

    RefmapHolder refmapHolder();

    int fabricLVTCompatibility();
}
