package org.sinytra.adapter.env.ctx;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.analysis.InheritanceHandler;
import org.sinytra.adapter.types.BytecodeFixerUpper;
import org.sinytra.adapter.util.provider.ClassLookup;

import java.util.Collection;

public interface PatchEnvironment {
    static PatchEnvironment create(RefmapHolder refmapHolder, ClassLookup cleanClassLookup, @Nullable BytecodeFixerUpper bytecodeFixerUpper,
                                   int fabricLVTCompatibility, AuditTrail auditTrail, @Nullable Collection<String> pkgNamespaces) {
        return new PatchEnvironmentImpl(refmapHolder, cleanClassLookup, bytecodeFixerUpper, fabricLVTCompatibility, auditTrail, pkgNamespaces);
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

    @Nullable
    Collection<String> getKnownNamespaces();

    boolean isKnownPackage(String pkg);

    /**
     * When enabled, mixin handlers that could not be adapted are removed from their class rather than left in
     * the jar, so the mod loads with those features disabled. Read from the
     * {@code connector.stripFailingMixins} system property.
     */
    default boolean stripFailingMixins() {
        return Boolean.getBoolean("connector.stripFailingMixins");
    }
}
