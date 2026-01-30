package org.sinytra.adapter.env.ctx;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.analysis.InheritanceHandler;
import org.sinytra.adapter.types.BytecodeFixerUpper;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.sinytra.adapter.util.provider.MixinClassLookup;

import java.util.HashMap;
import java.util.Map;

public final class PatchEnvironmentImpl implements PatchEnvironment {
    private final RefmapHolder refmapHolder;
    private final ClassLookup cleanClassLookup;
    private final ClassLookup dirtyClassLookup;
    private final @Nullable BytecodeFixerUpper bytecodeFixerUpper;
    private final MixinClassGenerator classGenerator;
    private final int fabricLVTCompatibility;
    private final AuditTrail auditTrail;
    
    private final Map<ClassLookup, InheritanceHandler> inheritanceHandlers = new HashMap<>();

    public PatchEnvironmentImpl(
        RefmapHolder refmapHolder,
        ClassLookup cleanClassLookup, ClassLookup dirtyClassLookup,
        @Nullable BytecodeFixerUpper bytecodeFixerUpper,
        MixinClassGenerator classGenerator,
        int fabricLVTCompatibility,
        AuditTrail auditTrail
    ) {
        this.refmapHolder = refmapHolder;
        this.cleanClassLookup = cleanClassLookup;
        this.dirtyClassLookup = dirtyClassLookup;
        this.bytecodeFixerUpper = bytecodeFixerUpper;
        this.classGenerator = classGenerator;
        this.fabricLVTCompatibility = fabricLVTCompatibility;
        this.auditTrail = auditTrail;
    }

    public PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility, AuditTrail auditTrail) {
        this(refmapHolder, cleanClassLookup, MixinClassLookup.INSTANCE, bytecodeFixerUpper, new MixinClassGeneratorImpl(), fabricLVTCompatibility, auditTrail);
    }

    public PatchEnvironmentImpl(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, ClassLookup dirtyClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper, int fabricLVTCompatibility) {
        this(refmapHolder, cleanClassLookup, dirtyClassLookup, bytecodeFixerUpper, new MixinClassGeneratorImpl(), fabricLVTCompatibility, new AuditTrailImpl());
    }

    @Override
    public RefmapHolder refmapHolder() {
        return refmapHolder;
    }

    @Override
    public ClassLookup cleanClassLookup() {
        return cleanClassLookup;
    }

    @Override
    public ClassLookup dirtyClassLookup() {
        return dirtyClassLookup;
    }

    @Override
    public @Nullable BytecodeFixerUpper bytecodeFixerUpper() {
        return bytecodeFixerUpper;
    }

    @Override
    public MixinClassGenerator classGenerator() {
        return classGenerator;
    }

    @Override
    public InheritanceHandler inheritanceHandler(ClassLookup lookup) {
        return this.inheritanceHandlers.computeIfAbsent(lookup, InheritanceHandler::new);
    }

    @Override
    public int fabricLVTCompatibility() {
        return fabricLVTCompatibility;
    }

    @Override
    public AuditTrail auditTrail() {
        return auditTrail;
    }
}
