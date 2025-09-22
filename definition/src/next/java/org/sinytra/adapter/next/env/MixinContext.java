package org.sinytra.adapter.next.env;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.ctx.MethodHelper;
import org.sinytra.adapter.patch.analysis.selector.AnnotationHandle;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.PatchContext;
import org.sinytra.adapter.patch.fixes.TypeAdapter;
import org.sinytra.adapter.patch.util.provider.ClassLookup;

public class MixinContext {
    private final ClassNode classNode;
    private final MethodNode methodNode;

    private final MethodHelper methodHelper;
    private final MethodContext methodContext;

    public MixinContext(ClassNode classNode, MethodNode methodNode, MethodContext methodContext) {
        this.classNode = classNode;
        this.methodNode = methodNode;
        this.methodHelper = new MethodHelper(this, methodContext.targetTypes());
        this.methodContext = methodContext;
    }

    public ClassNode classNode() {
        return this.classNode;
    }

    public MethodNode methodNode() {
        return this.methodNode;
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

    public String remap(String refmapEntry) {
        return patchContext().remap(refmapEntry);
    }

    public PatchContext patchContext() {
        return this.methodContext.patchContext();
    }

    @Deprecated
    public MethodContext legacy() {
        return this.methodContext;
    }
}
