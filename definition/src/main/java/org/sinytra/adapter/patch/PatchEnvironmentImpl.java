package org.sinytra.adapter.patch;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.analysis.InheritanceHandler;
import org.sinytra.adapter.patch.api.MixinClassGenerator;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.api.RefmapHolder;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.sinytra.adapter.patch.util.provider.MixinClassLookup;

public record PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, ClassLookup dirtyClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper,
                                   MixinClassGenerator classGenerator, InheritanceHandler inheritanceHandler, int fabricLVTCompatibility) implements PatchEnvironment {

    public PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper,
                                       MixinClassGenerator classGenerator, InheritanceHandler inheritanceHandler, int fabricLVTCompatibility) {
        this(refmapHolder, cleanClassLookup, MixinClassLookup.INSTANCE, bytecodeFixerUpper, classGenerator, inheritanceHandler, fabricLVTCompatibility);
    }

    public PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility) {
        this(refmapHolder, cleanClassLookup, bytecodeFixerUpper, new MixinClassGeneratorImpl(), new InheritanceHandler(MixinClassLookup.INSTANCE), fabricLVTCompatibility);
    }
}
