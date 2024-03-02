package org.sinytra.adapter.patch.api;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.PatchEnvironmentImpl;
import org.sinytra.adapter.patch.analysis.InheritanceHandler;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

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
