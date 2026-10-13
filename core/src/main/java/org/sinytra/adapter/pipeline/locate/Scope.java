package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.Targets;

public record Scope(Targets targets, Kind kind, int depth, MethodAlignment alignment, @Nullable Relocation relocation) {
    public enum Kind {
        SAME,
        REPLACEMENT,
        CALLED
    }

    public static Scope of(Targets targets) {
        return new Scope(targets, Kind.SAME, 0, MethodAlignment.align(targets.clean().methodNode(), targets.dirty().methodNode()), null);
    }

    public TargetPair method() {
        return this.targets.dirty();
    }

    public double similarity() {
        return this.alignment.similarity();
    }

    public Candidate candidate(AbstractInsnNode insn, MethodAlignment.Confidence confidence) {
        return new Candidate(method(), insn, confidence, this.relocation);
    }
}
