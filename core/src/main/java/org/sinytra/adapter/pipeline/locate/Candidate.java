package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.model.Relocation;
import org.sinytra.adapter.model.TargetPair;

public record Candidate(TargetPair target, AbstractInsnNode insn, MethodAlignment.Confidence confidence, @Nullable Relocation relocation) {
    public Candidate(TargetPair target, AbstractInsnNode insn, MethodAlignment.Confidence confidence) {
        this(target, insn, confidence, null);
    }
}
