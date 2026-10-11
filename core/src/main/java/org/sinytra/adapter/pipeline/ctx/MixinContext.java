package org.sinytra.adapter.pipeline.ctx;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodHelper;
import org.sinytra.adapter.api.BytecodeFixerUpper;
import org.sinytra.adapter.api.ClassLookup;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.api.TypeAdapter;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.ann.ClassTarget;
import org.sinytra.adapter.model.ann.RefMapper;
import org.sinytra.adapter.pipeline.audit.Auditor;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.pipeline.injector.MixinType;
import org.sinytra.adapter.pipeline.processor.Processors;
import org.sinytra.adapter.pipeline.resolver.Resolvers;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class MixinContext implements RefMapper, Auditor {
    private final MixinType mixinType;
    private final PatchContext patchContext;
    private final ClassTarget classTarget;
    private final ClassNode classNode;
    private final MethodNode methodNode;
    private final MethodNode originalMethodNode;
    private final Set<MixinFlag> flags;

    private final AnnotationHandle methodAnnotation;
    private final AnnotationHandle injectionPointAnnotation;
    private final List<Object> auditContext = new ArrayList<>();

    private final String mixinId;
    private final MethodHelper methodHelper;
    private final Resolvers resolvers = new Resolvers();
    private final Processors processors = new Processors();

    public MixinContext(MixinType mixinType, PatchContext patchContext, ClassTarget classTarget, ClassNode classNode, MethodNode methodNode, AnnotationHandle methodAnnotation, AnnotationHandle injectionPointAnnotation, Set<MixinFlag> flags) {
        this.mixinType = mixinType;
        this.patchContext = patchContext;
        this.classTarget = classTarget;
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.methodAnnotation = methodAnnotation;
        this.injectionPointAnnotation = injectionPointAnnotation;
        this.flags = flags;

        this.methodHelper = new MethodHelper(this, classTarget.getTypes());
        this.originalMethodNode = AdapterUtil.copyMethod(this.methodNode);

        this.mixinId = classNode.name + "#" + methodNode.name + methodNode.desc;
    }

    public MixinType getMixinType() {
        return this.mixinType;
    }

    public boolean hasFlag(MixinFlag flag) {
        return this.flags.contains(flag);
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
        BytecodeFixerUpper bfu = this.patchContext.environment().bytecodeFixerUpper();
        return bfu != null ? bfu.getTypeAdapter(from, to) : null;
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

    public <T> T audited(Object actor, Supplier<T> action) {
        this.auditContext.add(actor);
        try {
            return action.get();
        } finally {
            this.auditContext.removeLast();
        }
    }

    public void recordCtxAudit(String message, Object... args) {
        if (this.auditContext.isEmpty()) {
            throw new RuntimeException("Missing audit context object");
        }
        Object ctx = this.auditContext.getLast();
        recordAudit(ctx, message, args);
    }

    @Override
    public void recordAudit(Object actor, String message, Object... args) {
        environment().auditTrail().recordAudit(actor, this, message, args);
    }
}
