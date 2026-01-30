package org.sinytra.adapter.patch.processor.extract;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_METHOD;

// TODO Cleanup
public class MirrorableExtractMixin {

    public static PatchResult apply(MixinContext context, Recipe recipe, String destinationClass, MethodInsnNode destinationMethodInvocation) {
        TargetPair dirtyTarget = recipe.getNewCleanTarget();
        if (dirtyTarget == null) return PatchResult.PASS;

        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();
        PatchEnvironment environment = context.environment();

        Type selfType = Type.getObjectType(dirtyTarget.classNode().name);
        Type[] params = Type.getArgumentTypes(destinationMethodInvocation.desc);
        int selfIndex = Stream.of(Stream.iterate(0, i -> i < params.length, i -> i + 1)
                .filter(i -> params[i].equals(selfType))
                .toList())
            .filter(list -> list.size() == 1)
            .map(List::getFirst)
            .findFirst()
            .orElse(-1);
        if (selfIndex == -1) {
            return PatchResult.PASS;
        }

        List<AbstractInsnNode> callInsns = MethodCallAnalyzer.getMethodCallSrcInsns(dirtyTarget.methodNode(), destinationMethodInvocation);
        if (callInsns == null || callInsns.size() <= selfIndex) {
            return PatchResult.PASS;
        }
        AbstractInsnNode selfParamInsn = callInsns.get(selfIndex);
        if (!(selfParamInsn instanceof VarInsnNode varInsn) || varInsn.getOpcode() != Opcodes.ALOAD || varInsn.var != 0) {
            return PatchResult.PASS;
        }
        // Cool, out instance is passed into the method. Now let's inject there and call the old mixin method
        ClassNode generatedTarget = environment.classGenerator().getOrGenerateMixinClass(classNode, destinationClass, null);
        environment.refmapHolder().copyEntries(classNode.name, generatedTarget.name);
        // Generate a method with the same injector annotation
        String name = methodNode.name + "$adapter$mirror$" + AdapterUtil.randomString(5);
        List<Type> originalParams = List.of(Type.getArgumentTypes(methodNode.desc));
        List<Type> newParams = ImmutableList.<Type>builder().add(Type.getArgumentTypes(destinationMethodInvocation.desc)).add(TypeConstants.CI_TYPE).build();
        // Make sure we have all required params
        if (!new HashSet<>(newParams).containsAll(originalParams)) {
            return PatchResult.PASS;
        }

        String desc = Type.getMethodDescriptor(Type.VOID_TYPE, newParams.toArray(Type[]::new));
        // Change target
        context.methodAnnotation()
            .setOrAppendNonNull(AT_METHOD, List.of(
                MethodQualifier.create(destinationMethodInvocation).asDescriptor()
            ));

        MethodNode invokerMixinMethod = (MethodNode) generatedTarget.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, desc, null, null);
        invokerMixinMethod.visibleAnnotations = new ArrayList<>(methodNode.visibleAnnotations);
        // Make original mixin a unique public method
        methodNode.access = OpcodeUtil.setAccessVisibility(methodNode.access, Opcodes.ACC_PUBLIC);
        methodNode.visibleAnnotations.remove(context.methodAnnotation().unwrap());
        methodNode.visitAnnotation(MixinAnnotations.UNIQUE, true);
        // Now call the original mixin
        GeneratorAdapter gen = new GeneratorAdapter(invokerMixinMethod, invokerMixinMethod.access, invokerMixinMethod.name, invokerMixinMethod.desc);
        gen.newLabel();
        gen.loadArg(selfIndex);
        for (Type type : originalParams) {
            gen.loadArg(newParams.indexOf(type));
        }
        gen.invokeVirtual(selfType, new Method(methodNode.name, methodNode.desc));
        gen.newLabel();
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        return PatchResult.APPLY;
    }
}
