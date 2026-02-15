package org.sinytra.adapter.patch.processor.extract;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.MixinKeys;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.PROPERTY_ARGS_ONLY;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.PROPERTY_INDEX;

// Create an identical mixin in the destination class and call the old one
public class ProxyExtractMixinSub implements Processor {
    public static final ProxyExtractMixinSub INSTANCE = new ProxyExtractMixinSub();

    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (recipe.clean().getTargetClass().equals(dirty.getTargetClass()))
            return TxResult.PASS;

        if (!dirty.hasProperty(SpecialKeys.EXTRACT_ORIGIN_PARAM) || recipe.clean().hasProperty(MixinKeys.LOCALS))
            return TxResult.FAIL;

        TargetPair dirtyTarget = recipe.getNewCleanTarget();
        if (dirtyTarget == null) return TxResult.FAIL;

        ClassNode classNode = context.classNode();
        MethodNode methodNode = context.methodNode();
        PatchEnvironment environment = context.environment();

        int originParamIndex = dirty.getProperty(SpecialKeys.EXTRACT_ORIGIN_PARAM).orElseThrow();
        List<Type> destParams = Parameters.getParameterTypes(dirty.getTargetMethod().desc());
        Type instanceType = destParams.get(originParamIndex);
        String destinationClass = dirty.getTargetClass();

        // Cool, out instance is passed into the method. Now let's inject there and call the old mixin method
        ClassNode generatedTarget = environment.classGenerator().getOrGenerateMixinClass(classNode, destinationClass, null);
        environment.refmapHolder().copyEntries(classNode.name, generatedTarget.name);

        // Generate a method with the same injector annotation
        String name = methodNode.name + "$adapter$mirror$" + AdapterUtil.randomString(5);
        List<Type> originalParams = Parameters.getParameterTypes(methodNode.desc);
        List<Type> newParams = new ArrayList<>(originalParams);
        int capturedInstanceParamIndex = newParams.size();
        newParams.add(instanceType);
        String newDesc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc), newParams.toArray(Type[]::new));

        MethodNode invokerMixinMethod = (MethodNode) generatedTarget.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, name, newDesc, null, null);
        invokerMixinMethod.visibleAnnotations = new ArrayList<>(methodNode.visibleAnnotations);
        {
            // Add @Local to captured instance param
            AnnotationVisitor visitor = invokerMixinMethod.visitParameterAnnotation(capturedInstanceParamIndex, MixinAnnotations.LOCAL, false);
            visitor.visit(PROPERTY_ARGS_ONLY, true);
            visitor.visit(PROPERTY_INDEX, originParamIndex);
        }

        // Make original mixin a unique public method
        methodNode.access = OpcodeUtil.setAccessVisibility(methodNode.access, Opcodes.ACC_PUBLIC);
        methodNode.visibleAnnotations.remove(context.methodAnnotation().unwrap());
        methodNode.visitAnnotation(MixinAnnotations.UNIQUE, true);

        // Now call the original mixin
        GeneratorAdapter gen = new GeneratorAdapter(invokerMixinMethod, invokerMixinMethod.access, invokerMixinMethod.name, invokerMixinMethod.desc);
        gen.newLabel();
        gen.loadArg(capturedInstanceParamIndex);
        for (int i = 0; i < originalParams.size(); i++) {
            gen.loadArg(i);
        }
        gen.invokeVirtual(instanceType, new Method(methodNode.name, methodNode.desc));
        gen.newLabel();
        gen.returnValue();
        gen.newLabel();
        gen.endMethod();
        invokerMixinMethod.maxLocals = invokerMixinMethod.maxStack = 999;

        context.recordCtxAudit("Proxy (mirror) mixin to target %s", destinationClass);
        return TxResult.SUCCESS;
    }
}
