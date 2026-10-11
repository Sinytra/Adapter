package org.sinytra.adapter.patch.processor.extract;

import com.google.common.collect.ImmutableList;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.env.ctx.*;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.env.util.TypeConstants;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MethodQualifier;
import org.sinytra.adapter.util.OpcodeUtil;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_METHOD;

// TODO Cleanup
public class MirrorableExtractMixin {

    public static PatchResult apply(MixinContext context, Recipe recipe, String destinationClass, MethodInsnNode destinationMethodInvocation) {
        TargetPair dirtyTarget = recipe.getNewCleanTarget();
        if (dirtyTarget == null) return PatchResult.PASS;

        MethodNode methodNode = context.methodNode();

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

        List<AbstractInsnNode> callInsns = MethodAnalyzer.getMethodCallSrcInsns(dirtyTarget.methodNode(), destinationMethodInvocation);
        if (callInsns == null || callInsns.size() <= selfIndex) {
            return PatchResult.PASS;
        }
        AbstractInsnNode selfParamInsn = callInsns.get(selfIndex);
        if (!(selfParamInsn instanceof VarInsnNode varInsn) || varInsn.getOpcode() != Opcodes.ALOAD || varInsn.var != 0) {
            return PatchResult.PASS;
        }
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

        // Change target. The annotation is carried over to the mirror.
        context.methodAnnotation()
            .setOrAppendNonNull(AT_METHOD, List.of(
                MethodQualifier.create(destinationMethodInvocation).asDescriptor()
            ));

        String desc = Type.getMethodDescriptor(Type.VOID_TYPE, newParams.toArray(Type[]::new));
        generateMirror(context, destinationClass, desc, dirtyTarget.classNode(), gen -> gen.loadArg(selfIndex), argMapping);

        context.recordCtxAudit("Extract (mirror) mixin to target %s", destinationClass);
        return PatchResult.APPLY;
    }

    /**
     * Generate a mixin method in the generated mixin class for {@code destinationClass}. It carries the original injector annotation
     * and calls the original handler, which is turned into a public {@code @Unique} method.
     *
     * @param handlerOwner the class the original handler is merged into
     * @param loadInstance loads the handler's instance, if the handler isn't static
     * @param argIndices   the mirror parameter to pass as each handler parameter
     * @return the generated mirror method
     */
    public static MethodNode generateMirror(MixinContext context, String destinationClass, String desc, ClassNode handlerOwner, Consumer<GeneratorAdapter> loadInstance, int[] argIndices) {
        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();
        PatchEnvironment environment = context.environment();
        ClassNode generatedTarget = environment.classGenerator().getOrGenerateMixinClass(classNode, destinationClass, null);
        environment.refmapHolder().copyEntries(classNode.name, generatedTarget.name);

        // Generate a method with the same injector annotation
        String name = methodNode.name + "$adapter$mirror$" + AdapterUtil.randomString(5);
        MethodNode mirror = (MethodNode) generatedTarget.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, desc, null, null);
        mirror.visibleAnnotations = new ArrayList<>(methodNode.visibleAnnotations);

        // Make original mixin a unique public method
        methodNode.access = OpcodeUtil.setAccessVisibility(methodNode.access, Opcodes.ACC_PUBLIC);
        methodNode.visibleAnnotations.remove(context.methodAnnotation().unwrap());
        methodNode.visitAnnotation(MixinAnnotations.UNIQUE, true);

        // Now call the original mixin
        GeneratorAdapter gen = new GeneratorAdapter(mirror, mirror.access, mirror.name, mirror.desc);
        gen.newLabel();
        if (!MethodHelper.isStatic(methodNode)) {
            loadInstance.accept(gen);
        }
        for (int argIndex : argIndices) {
            gen.loadArg(argIndex);
        }
        invokeHandler(gen, methodNode, handlerOwner);
        gen.newLabel();
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        return mirror;
    }

    static void invokeHandler(GeneratorAdapter gen, MethodNode handler, ClassNode handlerOwner) {
        boolean isInterface = (handlerOwner.access & Opcodes.ACC_INTERFACE) != 0;
        int opcode = MethodHelper.isStatic(handler) ? Opcodes.INVOKESTATIC : isInterface ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
        gen.visitMethodInsn(opcode, handlerOwner.name, handler.name, handler.desc, isInterface);
    }
}
