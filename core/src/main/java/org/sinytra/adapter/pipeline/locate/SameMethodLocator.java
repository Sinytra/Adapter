package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.method.InheritanceHandler;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SameMethodLocator implements Locator {
    @Override
    public List<Candidate> locate(MixinContext context, Recipe recipe, Scope scope, AbstractInsnNode cleanInsn) {
        return find(context, scope, cleanInsn);
    }

    public static List<Candidate> find(MixinContext context, Scope scope, AbstractInsnNode cleanInsn) {
        MethodAlignment alignment = scope.alignment();
        List<MethodAlignment.Match> matches = alignment.find(cleanInsn);
        if (!matches.isEmpty()) {
            return matches.stream()
                .map(match -> scope.candidate(match.dirty(), match.confidence()))
                .toList();
        }

        if (OpcodeUtil.isReturnOpcode(cleanInsn.getOpcode())) {
            AbstractInsnNode dirtyReturn = findReturnReached(alignment, cleanInsn);
            return dirtyReturn != null ? List.of(scope.candidate(dirtyReturn, MethodAlignment.Confidence.POSITIONAL)) : List.of();
        }

        if (!(cleanInsn instanceof MethodInsnNode cleanCall)) {
            return List.of();
        }
        List<Candidate> replacements = alignment.findDirtyAround(cleanInsn).stream()
            .filter(insn -> insn instanceof MethodInsnNode dirtyCall && isReplacement(context, cleanCall, dirtyCall))
            .map(insn -> scope.candidate(insn, MethodAlignment.Confidence.POSITIONAL))
            .toList();
        if (!replacements.isEmpty() || scope.kind() == Scope.Kind.SAME) {
            return replacements;
        }
        List<Candidate> anywhere = new ArrayList<>();
        for (AbstractInsnNode insn : scope.method().methodNode().instructions) {
            if (insn instanceof MethodInsnNode dirtyCall && isReplacement(context, cleanCall, dirtyCall)) {
                anywhere.add(scope.candidate(insn, MethodAlignment.Confidence.POSITIONAL));
            }
        }
        return anywhere;
    }

    @Nullable
    private static AbstractInsnNode findReturnReached(MethodAlignment alignment, AbstractInsnNode cleanReturn) {
        AbstractInsnNode dirty = null;
        for (AbstractInsnNode insn = cleanReturn.getPrevious(); insn != null && dirty == null; insn = insn.getPrevious()) {
            if (insn instanceof JumpInsnNode || OpcodeUtil.isReturnOpcode(insn.getOpcode()) || insn.getOpcode() == Opcodes.ATHROW) {
                return null;
            }
            if (insn.getOpcode() >= 0) {
                dirty = alignment.resolve(insn);
            }
        }
        if (dirty == null) {
            return null;
        }

        Set<AbstractInsnNode> visited = new HashSet<>();
        for (AbstractInsnNode insn = dirty.getNext(); insn != null && visited.add(insn); insn = insn.getNext()) {
            if (insn.getOpcode() == cleanReturn.getOpcode()) {
                return insn;
            }
            if (insn instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.GOTO) {
                insn = jump.label;
            } else if (insn instanceof JumpInsnNode || OpcodeUtil.isReturnOpcode(insn.getOpcode()) || insn.getOpcode() == Opcodes.ATHROW) {
                return null;
            }
        }
        return null;
    }

    private static boolean isReplacement(MixinContext context, MethodInsnNode clean, MethodInsnNode dirty) {
        return clean.owner.equals(dirty.owner) && clean.name.equals(dirty.name)
            || clean.name.equals(dirty.name) && clean.desc.equals(dirty.desc) && isRelated(context, clean.owner, dirty.owner)
            || context.getTypeAdapter(Type.getObjectType(dirty.owner), Type.getObjectType(clean.owner)) != null
            && Type.getArgumentTypes(clean.desc).length == Type.getArgumentTypes(dirty.desc).length;
    }

    private static boolean isRelated(MixinContext context, String a, String b) {
        InheritanceHandler inheritance = context.environment().inheritanceHandler(context.dirtyLookup());
        return inheritance.isClassInherited(a, b) || inheritance.isClassInherited(b, a);
    }
}
