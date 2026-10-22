package org.sinytra.adapter.pipeline.processor.signature;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.ControlKeys;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.List;
import java.util.stream.StreamSupport;

public class StaticAccessStep implements SignatureStep {
    @Override
    public TxResult apply(MixinContext context, Configuration dirty, Recipe recipe) {
        Boolean cleanStatic = recipe.clean().getProperty(ControlKeys.STATIC).orElse(null);
        Boolean dirtyStatic = dirty.getProperty(ControlKeys.STATIC).orElse(null);
        if (cleanStatic == null || dirtyStatic == null || cleanStatic.equals(dirtyStatic)) {
            return TxResult.PASS;
        }

        MethodNode method = context.methodNode();
        boolean actuallyStatic = MethodAnalyzer.isStatic(method);
        if (dirtyStatic && !actuallyStatic) {
            // Mixin types add the target instance as the first parameter, which replaces usages of "this"
            Type[] args = Type.getArgumentTypes(method.desc);
            List<Type> targetTypes = context.targetTypes();
            boolean hasInstanceParam = args.length > 0 && targetTypes.size() == 1 && args[0].equals(targetTypes.getFirst());
            boolean usesThis = StreamSupport.stream(method.instructions.spliterator(), false)
                .map(AdapterUtil::handleLocalVarInsnValue)
                .anyMatch(handle -> handle != null && handle.get() == 0);
            if (usesThis && !hasInstanceParam) {
                return TxResult.FAIL;
            }

            context.recordCtxAudit("Made method static");
            AdapterUtil.makeStatic(method);
            return TxResult.SUCCESS;
        }
        if (!dirtyStatic && actuallyStatic) {
            context.recordCtxAudit("Made method non-static");
            method.access &= ~Opcodes.ACC_STATIC;
            LocalVariableNode firstParam = method.localVariables == null ? null
                : method.localVariables.stream().filter(lvn -> lvn.index == 0).findFirst().orElse(null);
            AdapterUtil.shiftLocals(method, 1);
            if (firstParam != null) {
                method.localVariables.add(new LocalVariableNode("this", Type.getObjectType(context.classNode().name).getDescriptor(), null, firstParam.start, firstParam.end, 0));
            }
            // TODO Must compute frames
            return TxResult.SUCCESS;
        }
        return TxResult.PASS;
    }

}
