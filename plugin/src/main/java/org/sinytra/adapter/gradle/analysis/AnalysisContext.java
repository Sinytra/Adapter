package org.sinytra.adapter.gradle.analysis;

import com.google.common.collect.BiMap;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.gradle.util.TraceCallback;
import org.sinytra.adapter.patch.api.Patch;

import java.util.List;

public class AnalysisContext {
    private final List<? super Patch> patches;
    private final ClassNode dirtyNode;
    private final BiMap<MethodNode, MethodNode> cleanToDirty;
    private final TraceCallback trace;

    public AnalysisContext(List<? super Patch> patches, ClassNode dirtyNode, BiMap<MethodNode, MethodNode> cleanToDirty, TraceCallback trace) {
        this.patches = patches;
        this.dirtyNode = dirtyNode;
        this.cleanToDirty = cleanToDirty;
        this.trace = trace;
    }

    public ClassNode getDirtyNode() {
        return this.dirtyNode;
    }

    public TraceCallback getTrace() {
        return this.trace;
    }

    public void addPatch(Patch patch) {
        this.patches.add(patch);
    }

    public MethodNode getCleanMethod(MethodNode dirty) {
        return this.cleanToDirty.inverse().get(dirty);
    }
}
