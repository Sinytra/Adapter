package org.sinytra.adapter.next.pipeline.resolver;

import com.google.common.collect.Multimap;
import org.jetbrains.annotations.Nullable;
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

    @Override
    public TxResult resolve(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getAtData() != null) {
            return TxResult.PASS;
        }
        if (!clean.getAtData().getValue().equals(AT_VAL_INVOKE)) {
            dirty.inheritAtData();
            return TxResult.SUCCESS;
        }

        MethodQualifier dirtyQualifier = dirty.getTargetMethod();
        if (dirtyQualifier == null) {
            return TxResult.FAIL;
        }

        MethodContext.TargetPair pair = context.methods().findMethod(context.dirtyLookup(), dirtyQualifier);
        if (pair == null) {
            return TxResult.FAIL;
        }
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(pair);
        if (!insns.isEmpty()) {
            dirty.setAtData(clean.getAtData());
            return TxResult.SUCCESS;
        }

        AtData replaced = findReplacedType(context, clean.getTargetMethod(), pair.methodNode(), clean.getAtData());
        if (replaced != null) {
            dirty.setAtData(replaced);
            return TxResult.SUCCESS;
        }

        return TxResult.FAIL;
    }

    @Nullable
    private static AtData findReplacedType(MixinContext context, MethodQualifier cleanQualifier, MethodNode dirtyMethod, AtData original) {
        MethodContext.TargetPair cleanPair = context.methods().findMethod(context.cleanLookup(), cleanQualifier);
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(cleanPair);
        if (insns.size() != 1 || !(insns.getFirst() instanceof MethodInsnNode cleanInsn)) {
            return null;
        }

        int insnRange = 5;
        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findSurroundingInstructions(cleanInsn, insnRange);

        Multimap<String, MethodInsnNode> dirtyCalls = MethodCallAnalyzer.getMethodCalls(dirtyMethod, new ArrayList<>());

        List<InstructionMatcher> dirtyMatchers = dirtyCalls.values().stream().map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, insnRange)).toList();
        for (InstructionMatcher dirtyMatcher : dirtyMatchers) {
            if (cleanMatcher.test(dirtyMatcher)) {
                MethodInsnNode minsn = (MethodInsnNode) dirtyMatcher.insn();
                Integer ordinal = original.getOrdinal().stream().boxed().findFirst().orElse(null);
                String target = Type.getObjectType(minsn.owner).getDescriptor() + minsn.name + minsn.desc;
                return new AtData(original.getValue(), target, ordinal);
            }
        }

        return null;
    }
}
