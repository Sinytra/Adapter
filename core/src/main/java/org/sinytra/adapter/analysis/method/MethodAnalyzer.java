package org.sinytra.adapter.analysis.method;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.constant.TypeConstants;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.*;

public class MethodAnalyzer {
    public static final String LAMBDA_PREFIX = "lambda$";

    public static boolean isLambda(MethodNode methodNode) {
        return methodNode.name.startsWith(LAMBDA_PREFIX);
    }

    public static boolean isLambda(MethodQualifier qualifier) {
        return qualifier.name() != null && qualifier.name().startsWith(LAMBDA_PREFIX);
    }

    public static boolean isDeprecated(MethodNode methodNode) {
        return AdapterUtil.hasAnnotation(methodNode.visibleAnnotations, TypeConstants.DEPRECATED);
    }

    public static boolean isDirtyDeprecatedMethod(MethodNode clean, MethodNode dirty) {
        return !isDeprecated(clean) && isDeprecated(dirty);
    }

    public record IndyHandle(InvokeDynamicInsnNode indy, int argIndex, Handle handle, MethodNode method) {
    }

    public static List<IndyHandle> findOwnIndyHandles(ClassNode cls, MethodNode method) {
        List<IndyHandle> handles = new ArrayList<>();
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof InvokeDynamicInsnNode indy) {
                for (int i = 0; i < indy.bsmArgs.length; i++) {
                    if (indy.bsmArgs[i] instanceof Handle handle && handle.getOwner().equals(cls.name)) {
                        MethodNode target = AdapterUtil.findMethod(cls, handle.getName(), handle.getDesc());
                        if (target != null) {
                            handles.add(new IndyHandle(indy, i, handle, target));
                        }
                    }
                }
            }
        }
        return handles;
    }

    public static List<AbstractInsnNode> findReturnInsns(MethodNode methodNode) {
        List<AbstractInsnNode> insns = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (OpcodeUtil.isReturnOpcode(insn.getOpcode())) {
                insns.add(insn);
            }
        }
        return insns;
    }

    public static Multimap<String, MethodInsnNode> getMethodCalls(MethodNode node) {
        ImmutableMultimap.Builder<String, MethodInsnNode> calls = ImmutableMultimap.builder();
        for (AbstractInsnNode insn : node.instructions) {
            if (insn instanceof MethodInsnNode minsn) {
                String qualifier = MethodQualifier.create(minsn).asDescriptor();
                calls.put(qualifier, minsn);
            }
        }
        return calls.build();
    }

    public static List<MethodInsnNode> findDirtyOnlyCalls(MethodNode clean, MethodNode dirty, boolean onlyOnce) {
        Multimap<String, MethodInsnNode> cleanCalls = getMethodCalls(clean);
        List<MethodInsnNode> dirtyOnly = new ArrayList<>();
        getMethodCalls(dirty).asMap().forEach((qualifier, calls) -> {
            if (!cleanCalls.containsKey(qualifier) && (!onlyOnce || calls.size() == 1)) {
                dirtyOnly.addAll(calls);
            }
        });
        return dirtyOnly;
    }

    public static List<MethodInsnNode> findCalls(MethodNode methodNode, MethodQualifier qualifier) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof MethodInsnNode minsn && qualifier.matches(minsn)) {
                calls.add(minsn);
            }
        }
        return calls;
    }
}
