package org.sinytra.adapter.patch.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.env.ann.AtData;
import org.sinytra.adapter.env.ctx.MixinContext;
import org.sinytra.adapter.env.ctx.TargetPair;
import org.sinytra.adapter.patch.Recipe;
import org.sinytra.adapter.patch.config.Configuration;
import org.sinytra.adapter.patch.config.MutableConfiguration;
import org.sinytra.adapter.patch.resolver.SubResolver;
import org.sinytra.adapter.util.MethodQualifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.env.util.MixinAnnotationConstants.AT_VAL_INVOKE_ASSIGN;

/**
 * Handles injection points whose target member MOVED to a different owner: NeoForge lifts a private static
 * helper onto the class of its first parameter, so owner and name change while the call keeps its shape. The
 * new name is deliberately ignored.
 * <p>
 * Matches the calls in the patched target method, not the receiving class: these helpers are usually
 * {@code default} methods on extension interfaces.
 */
public class RelocatedMemberSubResolver implements SubResolver {
    @Nullable
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        AtData at = recipe.clean().getAtData();
        if (at == null) {
            return null;
        }
        if (!AT_VAL_INVOKE.equals(at.getValue()) && !AT_VAL_INVOKE_ASSIGN.equals(at.getValue())) {
            return null;
        }

        String targetDesc = at.getTarget().orElse(null);
        if (targetDesc == null) {
            return null;
        }

        MethodQualifier original = MethodQualifier.parse(targetDesc).orElse(null);
        if (original == null || original.owner() == null || original.name() == null || original.desc() == null) {
            return null;
        }

        TargetPair dirtyTarget = recipe.getDirtyTarget();
        if (dirtyTarget == null) {
            return null;
        }

        // ChangedMemberDescriptorSubResolver covers descriptor-only changes; this one needs the member gone.
        if (context.methods().findOwnMethodPair(context.dirtyLookup(), original.ignoreDesc()) != null) {
            return null;
        }

        // "Lifted onto the first parameter's class" only applies to a static helper.
        TargetPair cleanOrigin = context.methods().findOwnMethodPair(context.cleanLookup(), original.ignoreDesc());
        if (cleanOrigin == null || !isStatic(cleanOrigin.methodNode())) {
            return null;
        }

        Type[] oldArgs = Type.getArgumentTypes(original.desc());
        if (oldArgs.length < 1) {
            return null;
        }
        Type receiver = oldArgs[0];
        if (receiver.getSort() != Type.OBJECT) {
            return null;
        }
        Type newReturn = Type.getReturnType(original.desc());
        Type[] newArgs = Arrays.copyOfRange(oldArgs, 1, oldArgs.length);

        List<MethodInsnNode> matches = findShiftedCalls(dirtyTarget.methodNode(), receiver, newArgs, newReturn);
        // Several call sites are fine; what must be unambiguous is the distinct owner+name+descriptor.
        if (matches.stream().map(call -> call.owner + call.name + call.desc).distinct().limit(2).count() != 1) {
            return null;
        }

        MethodInsnNode call = matches.getFirst();
        context.recordCtxAudit("Adjusting injection target for relocated member: %s -> %s.%s%s",
            original.asDescriptor(), call.owner, call.name, call.desc);

        return MutableConfiguration.create()
            .setAtData(at.withTarget(call));
    }

    /** Finds the calls matching the shifted shape: the old call with its receiver lifted out of the arguments. */
    private static List<MethodInsnNode> findShiftedCalls(MethodNode target, Type receiver, Type[] newArgs, Type newReturn) {
        List<MethodInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode insn = target.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode call)) {
                continue;
            }
            int opcode = call.getOpcode();
            if (opcode != Opcodes.INVOKEVIRTUAL && opcode != Opcodes.INVOKEINTERFACE && opcode != Opcodes.INVOKESPECIAL) {
                continue;
            }
            if (!call.owner.equals(receiver.getInternalName())) {
                continue;
            }
            if (!Arrays.equals(Type.getArgumentTypes(call.desc), newArgs)) {
                continue;
            }
            if (!Type.getReturnType(call.desc).equals(newReturn)) {
                continue;
            }
            matches.add(call);
        }
        return matches;
    }

    private static boolean isStatic(MethodNode methodNode) {
        return (methodNode.access & Opcodes.ACC_STATIC) != 0;
    }
}
