package org.sinytra.adapter.pipeline.locate;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.method.InheritanceHandler;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.List;

public final class SameMethodLocator implements Locator {
    @Override
    public List<Candidate> locate(MixinContext context, Recipe recipe, Targets targets, AbstractInsnNode cleanInsn) {
        MethodAlignment alignment = MethodAlignment.align(targets.clean().methodNode(), targets.dirty().methodNode());
        List<MethodAlignment.Match> matches = alignment.find(cleanInsn);
        if (!matches.isEmpty()) {
            return matches.stream()
                .map(match -> new Candidate(targets.dirty(), match.dirty(), match.confidence()))
                .toList();
        }

        if (!(cleanInsn instanceof MethodInsnNode cleanCall)) {
            return List.of();
        }
        return alignment.findDirtyAround(cleanInsn).stream()
            .filter(insn -> insn instanceof MethodInsnNode dirtyCall && isReplacement(context, cleanCall, dirtyCall))
            .map(insn -> new Candidate(targets.dirty(), insn, MethodAlignment.Confidence.POSITIONAL))
            .toList();
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
