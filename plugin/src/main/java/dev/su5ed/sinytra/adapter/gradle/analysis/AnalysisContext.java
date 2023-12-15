package dev.su5ed.sinytra.adapter.gradle.analysis;

import com.google.common.collect.BiMap;
import dev.su5ed.sinytra.adapter.gradle.util.TraceCallback;
import dev.su5ed.sinytra.adapter.patch.api.Patch;
import net.minecraftforge.srgutils.IMappingFile;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Optional;

public class AnalysisContext {
    private final List<? super Patch> patches;
    private final ClassNode dirtyNode;
    private final IMappingFile mappings;
    private final BiMap<MethodNode, MethodNode> cleanToDirty;
    private final TraceCallback trace;

    public AnalysisContext(List<? super Patch> patches, ClassNode dirtyNode, IMappingFile mappings, BiMap<MethodNode, MethodNode> cleanToDirty, TraceCallback trace) {
        this.patches = patches;
        this.dirtyNode = dirtyNode;
        this.mappings = mappings;
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

    public String remapMethod(String owner, String name, String desc) {
        return Optional.ofNullable(this.mappings.getClass(owner))
            .map(c -> c.getMethod(name, desc))
            .map(IMappingFile.INode::getMapped)
            .orElse(name);
    }

    public MethodNode getCleanMethod(MethodNode dirty) {
        return this.cleanToDirty.inverse().get(dirty);
    }
}
