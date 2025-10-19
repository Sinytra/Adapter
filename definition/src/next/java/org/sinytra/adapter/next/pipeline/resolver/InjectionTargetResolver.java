package org.sinytra.adapter.next.pipeline.resolver;

import com.google.common.collect.Multimap;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.TxResult;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.config.MutableConfiguration;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.MethodCallAnalyzer;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;

public class InjectionTargetResolver implements Resolver {
    private static final int INSN_RANGE = 5;

    @Override
    public TxResult resolve(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getAtData() != null) return TxResult.PASS;

        // Only support INVOKE for now
        if (!clean.getAtData().getValue().equals(AT_VAL_INVOKE)) {
            dirty.inheritAtData();
            return TxResult.SUCCESS;
        }

        MethodQualifier dirtyQualifier = dirty.getTargetMethod();
        if (dirtyQualifier == null) return TxResult.FAIL;

        MethodContext.TargetPair pair = context.methods().findOwnMethodPair(context.dirtyLookup(), dirtyQualifier);
        if (pair == null) return TxResult.FAIL;

        // Try reusing the original
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(pair);
        if (!insns.isEmpty()) {
            dirty.inheritAtData();
            return TxResult.SUCCESS;
        }

        // Find replacements
        if (findReplacedType(context, clean.getTargetMethod(), pair.methodNode(), clean.getAtData(), dirty)) {
            return TxResult.SUCCESS;
        }

        return TxResult.FAIL;
    }

    private static boolean findReplacedType(MixinContext context, MethodQualifier cleanQualifier, MethodNode dirtyMethod, AtData original, MutableConfiguration dirty) {
        // Find single clean target minsn
        MethodContext.TargetPair cleanPair = context.methods().findOwnMethodPair(context.cleanLookup(), cleanQualifier);
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(cleanPair);
        if (insns.size() != 1 || !(insns.getFirst() instanceof MethodInsnNode cleanInsn)) {
            return false;
        }

        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findSurroundingInstructions(cleanInsn, INSN_RANGE);
        Multimap<String, MethodInsnNode> dirtyCalls = MethodCallAnalyzer.getMethodCalls(dirtyMethod, new ArrayList<>());
        List<InstructionMatcher> dirtyMatchers = dirtyCalls.values().stream()
            .map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, INSN_RANGE))
            .toList();
        for (InstructionMatcher dirtyMatcher : dirtyMatchers) {
            if (cleanMatcher.test(dirtyMatcher) && matchesMethodCall(context, cleanInsn, (MethodInsnNode) dirtyMatcher.insn())) {
                MethodInsnNode minsn = (MethodInsnNode) dirtyMatcher.insn();
                String target = MethodQualifier.create(minsn).asDescriptor();
                dirty.setAtData(original.withTarget(target));
                return true;
            }
        }

        return false;
    }

    private static boolean matchesMethodCall(MixinContext context, MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn) {
        return cleanInsn.owner.equals(dirtyInsn.owner) && cleanInsn.name.equals(dirtyInsn.name)
            || context.getTypeAdapter(Type.getObjectType(dirtyInsn.owner), Type.getObjectType(cleanInsn.owner)) != null
            && Type.getArgumentTypes(cleanInsn.desc).length == Type.getArgumentTypes(dirtyInsn.desc).length;
    }
}
