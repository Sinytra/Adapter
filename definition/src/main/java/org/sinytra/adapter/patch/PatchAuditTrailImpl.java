package org.sinytra.adapter.patch;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchAuditTrail;
import org.slf4j.Logger;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.sinytra.adapter.patch.util.AdapterUtil.MIXINPATCH;

public class PatchAuditTrailImpl implements PatchAuditTrail {
    private static final DecimalFormat FORMAT = new DecimalFormat("##.00");
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<Candidate, AuditLog> auditTrail = new LinkedHashMap<>();
    private final Map<Candidate, Match> candidates = new ConcurrentHashMap<>();

    public void prepareMethod(MethodContext methodContext) {
        Candidate candidate = new Candidate(methodContext.getMixinClass(), methodContext.getMixinMethod());
        synchronized (this.auditTrail) {
            this.auditTrail.put(candidate, AuditLog.create(methodContext));
        }
    }

    public void recordAudit(Object transform, ClassNode classNode, String message, Object... args) {
        Candidate candidate = new Candidate(classNode, null);
        recordAudit(transform, null, candidate, message.formatted(args));
    }

    public void recordAudit(Object transform, MethodContext methodContext, String message, Object... args) {
        Candidate candidate = new Candidate(methodContext.getMixinClass(), methodContext.getMixinMethod());
        recordAudit(transform, methodContext.getMixinMethod(), candidate, message.formatted(args));
    }

    private void recordAudit(Object transform, @Nullable MethodNode methodNode, Candidate candidate, String message) {
        synchronized (this.auditTrail) {
            AuditLog auditLog = this.auditTrail.computeIfAbsent(candidate, k -> new AuditLog(methodNode != null ? methodNode.name + methodNode.desc : null, new ArrayList<>()));
            List<Pair<Object, StringBuilder>> entries = auditLog.entries();

            StringBuilder builder;
            if (entries.isEmpty() || entries.getLast().left() != transform) {
                entries.add(Pair.of(transform, builder = new StringBuilder()));
                builder.append("\n  >> Using ").append(transform.getClass().getName());
            } else {
                builder = entries.getLast().right();
            }

            builder.append("\n     - ").append(message);
            LOGGER.info(MIXINPATCH, "Applying [{}] {}", transform.getClass().getSimpleName(), message);
        }
    }

    public void recordResult(MethodContext methodContext, Match match) {
        Candidate candidate = new Candidate(methodContext.getMixinClass(), methodContext.getMixinMethod());
        this.candidates.compute(candidate, (key, prev) -> {
            Match maybeIgnore = match == Match.NONE && methodContext.isNotRequired() ? Match.IGNORED : match;
            return prev == null ? maybeIgnore : prev.or(maybeIgnore);
        });
    }

    public String getCompleteReport() {
        StringBuilder builder = new StringBuilder();

        getSummaryLines().forEach(l -> builder.append(l).append("\n"));

        List<Map.Entry<Candidate, Match>> failed = this.candidates.entrySet().stream()
            .filter(m -> m.getValue() == Match.NONE || m.getValue() == Match.IGNORED)
            .toList();
        if (!failed.isEmpty()) {
            builder.append("\n=============== Failed mixins ===============");
            failed.forEach(e -> builder.append("\n")
                .append(e.getValue() == Match.IGNORED ? "(ignored) " : "")
                .append(e.getKey().classNode().name)
                .append(" ")
                .append(e.getKey().methodNode().name)
                .append(e.getKey().methodNode().desc));
            builder.append("\n=============================================\n\n");
        } else {
            builder.append("\n");
        }

        this.auditTrail.forEach((candidate, auditLog) -> {
            if (auditLog.entries().isEmpty()) {
                return;
            }

            if (auditLog.originalMethod() == null) {
                builder.append("Mixin class ").append(candidate.classNode().name);
            } else {
                builder.append("Mixin method ").append(candidate.classNode().name).append(" ").append(auditLog.originalMethod());
            }

            for (Pair<Object, StringBuilder> record : auditLog.entries()) {
                builder.append(record.right());
            }

            builder.append("\n\n");
        });

        return builder.toString();
    }

    @Override
    public List<Candidate> getFailingMixins() {
        return this.candidates.entrySet().stream()
            .filter(m -> m.getValue() == Match.NONE)
            .map(Map.Entry::getKey)
            .toList();
    }

    @Override
    public Map<Candidate, AuditLog> getAuditTrail() {
        return this.auditTrail;
    }

    @Override
    public Map<Candidate, Match> getCandidates() {
        return this.candidates;
    }

    @Nullable
    @Override
    public Match getMatch(MethodContext methodContext) {
        Candidate candidate = new Candidate(methodContext.getMixinClass(), methodContext.getMixinMethod());
        return this.candidates.get(candidate);
    }

    @Override
    public void merge(PatchAuditTrail other) {
        synchronized (this.auditTrail) {
            this.auditTrail.putAll(other.getAuditTrail());
        }
        this.candidates.putAll(other.getCandidates());
    }

    @Override
    public void silenceClasses(Set<String> classes) {
        synchronized (this.candidates) {
            for (Candidate candidate : Set.copyOf(this.candidates.keySet())) {
                if (classes.contains(candidate.classNode().name)) {
                    this.candidates.put(candidate, Match.IGNORED);
                }
            }
        }
    }

    private List<String> getSummaryLines() {
        int total = this.candidates.size();
        int successful = (int) this.candidates.values().stream().filter(m -> m == Match.FULL).count();
        int partial = (int) this.candidates.values().stream().filter(m -> m == Match.PARTIAL).count();
        int failed = (int) this.candidates.values().stream().filter(m -> m == Match.NONE).count();
        int silenced = (int) this.candidates.values().stream().filter(m -> m == Match.IGNORED).count();
        double rate = (successful + partial) / (double) total * 100;
        double accuracy = (successful / (double) total + partial / (double) total / 2.0) * 100;

        return List.of(
            "==== Connector Mixin Patch Audit Summary ====",
            "Successful: %s".formatted(successful),
            "Partial: %s".formatted(partial),
            "Failed: %s%s".formatted(failed, silenced > 0 ? " (%s ignored)".formatted(silenced) : ""),
            "Success rate: %s%%        Accuracy: %s%%".formatted(FORMAT.format(rate), FORMAT.format(accuracy)),
            "============================================="
        );
    }

    public boolean hasFailingMixins() {
        return !getFailingMixins().isEmpty();
    }
}
