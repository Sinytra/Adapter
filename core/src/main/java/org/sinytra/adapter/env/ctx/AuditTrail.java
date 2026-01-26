package org.sinytra.adapter.env.ctx;

import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.MixinContext;
import org.sinytra.adapter.patch.config.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AuditTrail {
    static AuditTrail create() {
        return new AuditTrailImpl();
    }

    void prepareMethod(MixinContext context);

    void recordAudit(Object transform, ClassNode classNode, String message, Object... args);

    void recordAudit(Object transform, MixinContext context, String message, Object... args);

    void recordResult(MixinContext context, Configuration configuration, Match match);

    String getCompleteReport();

    boolean hasFailingMixins();

    List<Candidate> getFailingMixins();

    Map<Candidate, AuditLog> getAuditTrail();

    Map<Candidate, Match> getCandidates();

    @Nullable
    Match getMatch(MixinContext context);

    void merge(AuditTrail other);

    void silenceClasses(Set<String> classes);

    enum Match {
        /**
         * Failed to patch mixin
         */
        NONE,
        /**
         * Failed to patch mixin, but we're ignoring this error
         */
        IGNORED,
        /**
         * Mixin patched, but it not be accurate
         */
        PARTIAL,
        /**
         * Mixin patched with high precision
         */
        FULL;

        public Match or(Match other) {
            if (this == NONE && other != NONE) {
                return other;
            }
            if (this == IGNORED && other != NONE) {
                return other;
            }
            if (this == PARTIAL && other == FULL) {
                return FULL;
            }
            return this;
        }
    }

    record Candidate(ClassNode classNode, MethodNode methodNode) {}

    record AuditLog(@Nullable String originalMethod, List<Pair<Object, StringBuilder>> entries) {
        public static AuditLog create(MixinContext context) {
            return new AuditLog(context.methodNode().name + context.methodNode().desc, new ArrayList<>());
        }
    }
}
