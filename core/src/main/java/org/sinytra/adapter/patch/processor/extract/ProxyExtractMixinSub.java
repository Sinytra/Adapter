package org.sinytra.adapter.patch.processor.extract;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.env.param.Parameters;
import org.sinytra.adapter.env.util.MixinAnnotations;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.TxResult;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.key.SpecialKeys;
import org.sinytra.adapter.patch.processor.Processor;
import org.sinytra.adapter.analysis.locals.LocalVarAnalyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.PROPERTY_ARGS_ONLY;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.PROPERTY_INDEX;

// Create an identical mixin in the destination class and call the old one
public class ProxyExtractMixinSub implements Processor {
    public static final ProxyExtractMixinSub INSTANCE = new ProxyExtractMixinSub();

    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        if (recipe.clean().getTargetClass().equals(dirty.getTargetClass()))
            return TxResult.PASS;

        if (!dirty.hasProperty(SpecialKeys.EXTRACT_ORIGIN_PARAM) || LocalVarAnalyzer.capturesLocals(context))
            return TxResult.FAIL;

        TargetPair dirtyTarget = recipe.getNewCleanTarget();
        if (dirtyTarget == null) return TxResult.FAIL;

        MethodNode methodNode = context.methodNode();

        int originParamIndex = dirty.getProperty(SpecialKeys.EXTRACT_ORIGIN_PARAM).orElseThrow();
        if (dirty.getTargetMethod() == null || dirty.getTargetMethod().desc() == null) return TxResult.FAIL;

        List<Type> destParams = Parameters.getParameterTypes(dirty.getTargetMethod().desc());
        Type instanceType = destParams.get(originParamIndex);
        String destinationClass = dirty.getTargetClass();

        // Cool, out instance is passed into the method. Now let's inject there and call the old mixin method
        List<Type> originalParams = Parameters.getParameterTypes(methodNode.desc);
        List<Type> newParams = new ArrayList<>(originalParams);
        int capturedInstanceParamIndex = newParams.size();
        newParams.add(instanceType);
        String newDesc = Type.getMethodDescriptor(Type.getReturnType(methodNode.desc), newParams.toArray(Type[]::new));

        // The original handler is merged into the original target class
        Type handlerOwner = Type.getObjectType(dirtyTarget.classNode().name);
        MethodNode mirror = MirrorableExtractMixin.generateMirror(context, destinationClass, newDesc, dirtyTarget.classNode(), gen -> {
            gen.loadArg(capturedInstanceParamIndex);
            if (!instanceType.equals(handlerOwner)) {
                gen.checkCast(handlerOwner);
            }
        }, IntStream.range(0, originalParams.size()).toArray());
        // Add @Local to captured instance param
        AnnotationVisitor visitor = mirror.visitParameterAnnotation(capturedInstanceParamIndex, MixinAnnotations.LOCAL, false);
        visitor.visit(PROPERTY_ARGS_ONLY, true);
        visitor.visit(PROPERTY_INDEX, originParamIndex);
        mirror.maxLocals = mirror.maxStack = 999;

        context.recordCtxAudit("Proxy (mirror) mixin to target %s", destinationClass);
        return TxResult.SUCCESS;
    }
}
