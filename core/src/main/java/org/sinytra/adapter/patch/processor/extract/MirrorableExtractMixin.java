package org.sinytra.adapter.patch.processor.extract;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.method.MethodCallAnalyzer;
import org.sinytra.adapter.env.ctx.MethodHelper;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.PatchResult;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
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
        List<Type> originalParams = Parameters.getParameterTypes(methodNode.desc);
        List<Type> newParams = ImmutableList.<Type>builder().add(Type.getArgumentTypes(destinationMethodInvocation.desc)).add(TypeConstants.CI_TYPE).build();
        // Make sure we have all required params, matching parameters of the same type in order
        int[] argMapping = new int[originalParams.size()];
        boolean[] mappedArgs = new boolean[newParams.size()];
        for (int i = 0; i < originalParams.size(); i++) {
            int argIndex = -1;
            for (int j = 0; j < newParams.size(); j++) {
                if (!mappedArgs[j] && newParams.get(j).equals(originalParams.get(i))) {
                    argIndex = j;
                    break;
                }
            }
            if (argIndex == -1) {
                return PatchResult.PASS;
            }
            mappedArgs[argIndex] = true;
            argMapping[i] = argIndex;
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
        if (!MethodHelper.isStatic(methodNode)) {
            gen.loadArg(selfIndex);
        }
        for (int argIndex : argMapping) {
            gen.loadArg(argIndex);
        }
        invokeHandler(gen, methodNode, dirtyTarget.classNode());
        gen.newLabel();
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();

        context.recordCtxAudit("Extract (mirror) mixin to target %s", destinationClass);
        return PatchResult.APPLY;
    }

    static void invokeHandler(GeneratorAdapter gen, MethodNode handler, ClassNode handlerOwner) {
        boolean isInterface = (handlerOwner.access & Opcodes.ACC_INTERFACE) != 0;
        int opcode = MethodHelper.isStatic(handler) ? Opcodes.INVOKESTATIC : isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
        gen.visitMethodInsn(opcode, handlerOwner.name, handler.name, handler.desc, isInterface);
    }
}
