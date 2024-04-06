package org.sinytra.adapter.patch.transformer;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.patch.analysis.InheritanceHandler;
import org.sinytra.adapter.patch.analysis.params.ParametersDiff;
import org.sinytra.adapter.patch.api.*;
import org.sinytra.adapter.patch.fixes.BytecodeFixerUpper;
import org.sinytra.adapter.patch.transformer.param.TransformParameters;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public record SoftMethodParamsPatch(String replacementTarget) implements MethodTransform {
    public static final Codec<SoftMethodParamsPatch> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("replacementTarget").forGetter(SoftMethodParamsPatch::replacementTarget)
    ).apply(instance, SoftMethodParamsPatch::new));

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(MixinConstants.INJECT);
    }

    @Override
    public Patch.Result apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchContext context) {
        MethodQualifier targetQualifier = methodContext.getTargetMethodQualifier();
        if (targetQualifier != null) {
            List<Pair<Integer, Type>> replacements = determineAutomaticReplacements(targetQualifier, methodNode, context, this.replacementTarget);
            if (!replacements.isEmpty()) {
                TransformParameters patch = TransformParameters.builder()
                    .replacements(replacements)
                    .build();
                return Patch.Result.APPLY.or(patch.apply(classNode, methodNode, methodContext, context));
            }
        }
        return Patch.Result.PASS;
    }

    private List<Pair<Integer, Type>> determineAutomaticReplacements(MethodQualifier targetQualifier, MethodNode methodNode, PatchContext context, String replacement) {
        BytecodeFixerUpper bfu = context.environment().bytecodeFixerUpper();
        if (bfu == null) {
            return List.of();
        }
        MethodQualifier newQualifier = MethodQualifier.create(replacement).orElse(null);
        if (newQualifier == null) {
            return List.of();
        }
        Type[] args = Type.getArgumentTypes(targetQualifier.desc());
        Type[] newArgs = Type.getArgumentTypes(newQualifier.desc());
        ParametersDiff diff = ParametersDiff.compareTypeParameters(args, newArgs);
        if (!diff.replacements().isEmpty() && diff.insertions().isEmpty() && diff.swaps().isEmpty()) {
            return diff.replacements().stream()
                .filter(pair -> {
                    int index = pair.getFirst();
                    Type type = pair.getSecond();
                    Type original = args[pair.getFirst()];
                    return original.getSort() == Type.OBJECT && type.getSort() == Type.OBJECT
                        && (bfu.getTypeAdapter(pair.getSecond(), original) != null
                        || permittedTypeNarrowing(index, original, type, context.environment().inheritanceHandler(), methodNode));
                })
                .toList();
        }
        return List.of();
    }

    private static boolean permittedTypeNarrowing(int index, Type original, Type updated, InheritanceHandler inheritanceHandler, MethodNode methodNode) {
        if (inheritanceHandler.isClassInherited(original.getInternalName(), updated.getInternalName())) {
            int lvtIndex = AdapterUtil.getLVTIndexForParam(methodNode, index, original);
            for (AbstractInsnNode insn : methodNode.instructions) {
                if (insn instanceof VarInsnNode varInsn && varInsn.var == lvtIndex) {
                    MethodInsnNode lastMethodCall = null;
                    for (AbstractInsnNode next = insn.getNext(); next != null && !(next instanceof LabelNode); next = next.getNext()) {
                        if (next instanceof MethodInsnNode minsn) {
                            lastMethodCall = minsn;
                        }
                    }
                    if (!lastMethodCall.owner.equals(original.getInternalName()) || !inheritanceHandler.isMethodOverriden(lastMethodCall.owner, lastMethodCall.name, lastMethodCall.desc)) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }
}
