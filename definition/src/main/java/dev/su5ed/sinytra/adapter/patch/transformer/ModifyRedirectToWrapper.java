package dev.su5ed.sinytra.adapter.patch.transformer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import dev.su5ed.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import dev.su5ed.sinytra.adapter.patch.api.*;
import dev.su5ed.sinytra.adapter.patch.util.AdapterUtil;
import dev.su5ed.sinytra.adapter.patch.util.LocalVariableLookup;
import dev.su5ed.sinytra.adapter.patch.util.MethodQualifier;
import dev.su5ed.sinytra.adapter.patch.util.OpcodeUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record ModifyRedirectToWrapper(MethodQualifier originalTarget, MethodQualifier newTarget) implements MethodTransform {
    private static final Type OPERATION_TYPE = Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation");

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.REDIRECT);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        LocalVariableLookup lookup = new LocalVariableLookup(methodNode.localVariables);
        Type newOwnerType = Type.getType(this.newTarget.owner());
        boolean sameOwnerType = Type.getType(this.originalTarget.owner()).equals(newOwnerType);

        int offset = ((methodNode.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0)
            + (!sameOwnerType ? -1 : 0);
        Set<Integer> paramLvtIndices = IntStream.range(offset + 1, Type.getArgumentTypes(methodNode.desc).length + offset)
            .map(i -> lookup.getByOrdinal(i).index)
            .boxed()
            .collect(Collectors.toSet());
        List<List<AbstractInsnNode>> insns = MethodCallAnalyzer.getInvocationInsns(methodNode, this.originalTarget);
        if (insns.isEmpty()) {
            return Patch.Result.PASS;
        }
        List<AbstractInsnNode> flatInsns = insns.stream().flatMap(Collection::stream).toList();
        Set<Integer> loadedLocals = insns.stream()
            .map(i -> i instanceof VarInsnNode varInsn ? varInsn.var : null)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && paramLvtIndices.contains(varInsn.var) && !loadedLocals.contains(varInsn.var) && !flatInsns.contains(insn)) {
                return Patch.Result.PASS;
            }
        }

        ModifyMethodParams removeOldParamsPatch = ModifyMethodParams.builder()
            .chain(b -> {
                for (int i = offset; i < Type.getArgumentTypes(methodNode.desc).length; i++) {
                    b.remove(i);
                }
            })
            .ignoreOffset()
            .build();
        removeOldParamsPatch.apply(classNode, methodNode, methodContext, context);

        List<Type> args = Lists.reverse(ImmutableList.<Type>builder()
            .add(sameOwnerType ? new Type[0] : new Type[]{newOwnerType})
            .add(Type.getArgumentTypes(this.newTarget.desc()))
            .build());
        ModifyMethodParams addNewParamsPatch = ModifyMethodParams.builder()
            .chain(b -> {
                for (int i = 0; i < args.size(); i++) {
                    Type type = args.get(i);
                    b.insert(i, type);
                }
                b.insert(args.size(), OPERATION_TYPE);
            })
            .ignoreOffset()
            .build();
        addNewParamsPatch.apply(classNode, methodNode, methodContext, context);

        LocalVariableLookup updatedLookup = new LocalVariableLookup(methodNode.localVariables);
        LocalVariableNode operationVar = updatedLookup.getForType(OPERATION_TYPE).get(0);
        int operationParamOrdinal = updatedLookup.getOrdinal(operationVar);
        List<LocalVariableNode> localVars = new ArrayList<>();
        for (int i = 1; i < operationParamOrdinal; i++) {
            localVars.add(updatedLookup.getByOrdinal(i));
        }
        InsnList originalCallInsns = AdapterUtil.insnsWithAdapter(a -> {
            a.load(operationVar.index, OPERATION_TYPE);
            a.iconst(localVars.size());
            a.newarray(Type.getObjectType("java/lang/Object"));
            for (int i = 0; i < localVars.size(); i++) {
                LocalVariableNode lvn = localVars.get(i);
                Type type = Type.getType(lvn.desc);

                a.dup();
                a.iconst(i);
                a.load(lvn.index, type);
                a.visitInsn(Opcodes.AASTORE);
            }
            a.invokeinterface("com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call", "([Ljava/lang/Object;)Ljava/lang/Object;");
            OpcodeUtil.castObjectType(Type.getReturnType(this.newTarget.desc()), a);
        });
        for (List<AbstractInsnNode> list : insns) {
            methodNode.instructions.insertBefore(list.get(0), originalCallInsns);
            list.forEach(methodNode.instructions::remove);
        }

        return Patch.Result.APPLY;
    }
}
