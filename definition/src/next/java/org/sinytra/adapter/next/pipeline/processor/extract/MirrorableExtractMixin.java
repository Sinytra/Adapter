package org.sinytra.adapter.next.pipeline.processor.extract;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.transformer.operation.unit.ModifyInjectionTarget;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

public record MirrorableExtractMixin(String destinationClass, MethodInsnNode destinationMethodInvocation) implements MethodTransform {
    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        Type selfType = Type.getObjectType(methodContext.findDirtyInjectionTarget().classNode().name);
        Type[] params = Type.getArgumentTypes(this.destinationMethodInvocation.desc);
        int selfIndex = Stream.of(Stream.iterate(0, i -> i < params.length, i -> i + 1)
                .filter(i -> params[i].equals(selfType))
                .toList())
            .filter(list -> list.size() == 1)
            .map(List::getFirst)
            .findFirst()
            .orElse(-1);
        if (selfIndex == -1) {
            return Patch.Result.PASS;
        }

        List<AbstractInsnNode> callInsns = MethodCallAnalyzer.getMethodCallSrcInsns(methodContext.findDirtyInjectionTarget().methodNode(), this.destinationMethodInvocation);
        if (callInsns == null || callInsns.size() <= selfIndex) {
            return Patch.Result.PASS;
        }
        AbstractInsnNode selfParamInsn = callInsns.get(selfIndex);
        if (!(selfParamInsn instanceof VarInsnNode varInsn) || varInsn.getOpcode() != Opcodes.ALOAD || varInsn.var != 0) {
            return Patch.Result.PASS;
        }
        // Cool, out instance is passed into the method. Now let's inject there and call the old mixin method
        ClassNode generatedTarget = methodContext.patchContext().environment().classGenerator().getOrGenerateMixinClass(methodContext.getMixinClass(), this.destinationClass, null);
        methodContext.patchContext().environment().refmapHolder().copyEntries(methodContext.getMixinClass().name, generatedTarget.name);
        // Generate a method with the same injector annotation
        MethodNode originalMixinMethod = methodContext.getMixinMethod();
        String name = originalMixinMethod.name + "$adapter$mirror$" + AdapterUtil.randomString(5);
        List<Type> originalParams = List.of(Type.getArgumentTypes(originalMixinMethod.desc));
        List<Type> newParams = ImmutableList.<Type>builder().add(Type.getArgumentTypes(this.destinationMethodInvocation.desc)).add(MixinConstants.CI_TYPE).build();
        // Make sure we have all required params
        if (!new HashSet<>(newParams).containsAll(originalParams)) {
            return Patch.Result.PASS;
        }

        String desc = Type.getMethodDescriptor(Type.VOID_TYPE, newParams.toArray(Type[]::new));
        // Change target
        Patch.Result result = new ModifyInjectionTarget(List.of(MethodQualifier.create(destinationMethodInvocation).asDescriptor())).apply(methodContext);
        if (result == Patch.Result.PASS) {
            return Patch.Result.PASS;
        }

        MethodNode invokerMixinMethod = (MethodNode) generatedTarget.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, desc, null, null);
        invokerMixinMethod.visibleAnnotations = new ArrayList<>(originalMixinMethod.visibleAnnotations);
        // Make original mixin a unique public method
        originalMixinMethod.access = OpcodeUtil.setAccessVisibility(originalMixinMethod.access, Opcodes.ACC_PUBLIC);
        originalMixinMethod.visibleAnnotations.remove(methodContext.methodAnnotation().unwrap());
        originalMixinMethod.visitAnnotation(MixinConstants.UNIQUE, true);
        // Now call the original mixin
        GeneratorAdapter gen = new GeneratorAdapter(invokerMixinMethod, invokerMixinMethod.access, invokerMixinMethod.name, invokerMixinMethod.desc);
        gen.newLabel();
        gen.loadArg(selfIndex);
        for (Type type : originalParams) {
            gen.loadArg(newParams.indexOf(type));
        }
        gen.invokeVirtual(selfType, new Method(originalMixinMethod.name, originalMixinMethod.desc));
        gen.newLabel();
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        return Patch.Result.APPLY;
    }
}
