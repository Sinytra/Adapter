package org.sinytra.adapter.next.env;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ann.ClassTarget;
import org.sinytra.adapter.next.env.ctx.MethodHelper;
import org.sinytra.adapter.next.env.ctx.RefMapper;
import org.sinytra.adapter.next.pipeline.processor.Processors;
import org.sinytra.adapter.next.pipeline.resolver.Resolvers;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

import java.util.List;

public class MixinContext implements RefMapper {
    private final String mixinId;
    private final ClassTarget classTarget;
    private final ClassNode classNode;
    private final MethodNode methodNode;
    private final MethodNode originalMethodNode;

    private final MethodHelper methodHelper;
    private final MethodContext methodContext;

    private final Resolvers resolvers = new Resolvers();
    private final Processors processors = new Processors();

    public MixinContext(ClassTarget classTarget, ClassNode classNode, MethodNode methodNode, MethodContext methodContext) {
        this.mixinId = classNode.name + "#" + methodNode.name + methodNode.desc;

        this.classTarget = classTarget;
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.methodHelper = new MethodHelper(this, classTarget.getTypes());
        this.methodContext = methodContext;
        this.originalMethodNode = AdapterUtil.copyMethod(this.methodNode);
    }

    public String getMixinId() {
        return this.mixinId;
    }

    // TODO Move out
    public Resolvers getResolvers() {
        return resolvers;
    }

    public Processors getProcessors() {
        return processors;
    }

    public ClassTarget classTarget() {
        return this.classTarget;
    }

    public ClassNode classNode() {
        return this.classNode;
    }

    public MethodNode methodNode() {
        return this.methodNode;
    }

    public MethodNode unmodifiedMethodNode() {
        return this.originalMethodNode;
    }

    public MethodHelper methods() {
        return this.methodHelper;
    }

    public ClassLookup cleanLookup() {
        return this.methodContext.patchContext().environment().cleanClassLookup();
    }

    public ClassLookup dirtyLookup() {
        return this.methodContext.patchContext().environment().dirtyClassLookup();
    }

    public AnnotationHandle methodAnnotation() {
        return this.methodContext.methodAnnotation();
    }

    @Nullable
    public AnnotationHandle injectionPointAnnotation() {
        return this.methodContext.injectionPointAnnotation();
    }

    @Nullable
    public TypeAdapter getTypeAdapter(Type from, Type to) {
        return this.methodContext.patchContext().environment().bytecodeFixerUpper().getTypeAdapter(from, to);
    }

    @Override
    public String remap(String refmapEntry) {
        return patchContext().remap(refmapEntry);
    }

    public PatchContext patchContext() {
        return this.methodContext.patchContext();
    }

    public boolean isStatic() {
        return MethodHelper.isStatic(this.methodNode);
    }

    @Deprecated
    public MethodContext legacy() {
        return this.methodContext;
    }

    public List<Type> targetTypes() {
        return this.classTarget.getTypes();
    }

    public PatchEnvironment environment() {
        return patchContext().environment();
    }
}
