package org.sinytra.adapter.env.ctx;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.analysis.InheritanceHandler;
import org.sinytra.adapter.types.BytecodeFixerUpper;
import org.sinytra.adapter.util.provider.ClassLookup;

public interface PatchEnvironment {
    static PatchEnvironment create(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility, AuditTrail auditTrail) {
        return new PatchEnvironmentImpl(refmapHolder, cleanClassLookup, bytecodeFixerUpper, fabricLVTCompatibility, auditTrail);
    }

    static PatchEnvironment create(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, ClassLookup dirtyClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility) {
        return new PatchEnvironmentImpl(refmapHolder, cleanClassLookup, dirtyClassLookup, bytecodeFixerUpper, fabricLVTCompatibility);
    }

    MixinClassGenerator classGenerator();

    ClassLookup cleanClassLookup();

    ClassLookup dirtyClassLookup();

    @Nullable
    BytecodeFixerUpper bytecodeFixerUpper();

    InheritanceHandler inheritanceHandler(ClassLookup lookup);

    RefmapHolder refmapHolder();

    int fabricLVTCompatibility();

    AuditTrail auditTrail();
}
