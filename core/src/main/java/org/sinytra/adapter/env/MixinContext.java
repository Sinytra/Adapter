package org.sinytra.adapter.env;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ann.ClassTarget;
import org.sinytra.adapter.env.ctx.*;
import org.sinytra.adapter.patch.processor.Processors;
import org.sinytra.adapter.patch.resolver.Resolvers;
import org.sinytra.adapter.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.types.TypeAdapter;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.provider.ClassLookup;

import java.util.List;

public class MixinContext implements RefMapper, Auditor {
    private final PatchContext patchContext;
    private final ClassTarget classTarget;
    private final ClassNode classNode;
    private final MethodNode methodNode;
    private final MethodNode originalMethodNode;
    
    private final AnnotationHandle methodAnnotation;
    private final AnnotationHandle injectionPointAnnotation;

    private final String mixinId;
    private final MethodHelper methodHelper;
    private final Resolvers resolvers = new Resolvers();
    private final Processors processors = new Processors();

    public MixinContext(PatchContext patchContext, ClassTarget classTarget, ClassNode classNode, MethodNode methodNode, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation) {
        this.patchContext = patchContext;
        this.classTarget = classTarget;
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.methodAnnotation = methodAnnotation;
        this.injectionPointAnnotation = injectionPointAnnotation;

        this.methodHelper = new MethodHelper(this, classTarget.getTypes());
        this.originalMethodNode = AdapterUtil.copyMethod(this.methodNode);
        
        this.mixinId = classNode.name + "#" + methodNode.name + methodNode.desc;
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
        return this.patchContext.environment().cleanClassLookup();
    }

    public ClassLookup dirtyLookup() {
        return this.patchContext.environment().dirtyClassLookup();
    }

    public AnnotationHandle methodAnnotation() {
        return this.methodAnnotation;
    }

    @Nullable
    public AnnotationHandle injectionPointAnnotation() {
        return this.injectionPointAnnotation;
    }

    @Nullable
    public TypeAdapter getTypeAdapter(Type from, Type to) {
        return this.patchContext.environment().bytecodeFixerUpper().getTypeAdapter(from, to);
    }

    @Override
    public String remap(String refmapEntry) {
        return patchContext().remap(refmapEntry);
    }

    public PatchContext patchContext() {
        return this.patchContext;
    }

    public boolean isStatic() {
        return MethodHelper.isStatic(this.methodNode);
    }

    public List<Type> targetTypes() {
        return this.classTarget.getTypes();
    }

    public PatchEnvironment environment() {
        return patchContext().environment();
    }

    @Override
    public void recordAudit(Object transform, String message, Object... args) {
        environment().auditTrail().recordAudit(transform, this, message, args);
    }
}
