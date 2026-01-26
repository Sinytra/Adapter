package org.sinytra.adapter.next.env.ctx;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.analysis.InheritanceHandler;
import org.sinytra.adapter.next.types.BytecodeFixerUpper;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.sinytra.adapter.patch.util.provider.MixinClassLookup;

public record PatchEnvironmentImpl(
    RefmapHolder refmapHolder,
    ClassLookup cleanClassLookup, ClassLookup dirtyClassLookup,
    @Nullable BytecodeFixerUpper bytecodeFixerUpper,
    MixinClassGenerator classGenerator,
    InheritanceHandler inheritanceHandler,
    int fabricLVTCompatibility,
    AuditTrail auditTrail
) implements PatchEnvironment {

    public PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility, AuditTrail auditTrail) {
        this(refmapHolder, cleanClassLookup, MixinClassLookup.INSTANCE, bytecodeFixerUpper, new MixinClassGeneratorImpl(), new InheritanceHandler(MixinClassLookup.INSTANCE), fabricLVTCompatibility, auditTrail);
    }
    
    public PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, ClassLookup dirtyClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility) {
        this(refmapHolder, cleanClassLookup, dirtyClassLookup, bytecodeFixerUpper, new MixinClassGeneratorImpl(), new InheritanceHandler(MixinClassLookup.INSTANCE), fabricLVTCompatibility, new AuditTrailImpl());
    }
}
