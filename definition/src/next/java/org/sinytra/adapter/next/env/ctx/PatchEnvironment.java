package org.sinytra.adapter.next.env.ctx;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.analysis.InheritanceHandler;
import org.sinytra.adapter.next.types.BytecodeFixerUpper;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

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

    InheritanceHandler inheritanceHandler();

    RefmapHolder refmapHolder();

    int fabricLVTCompatibility();

    AuditTrail auditTrail();
}
