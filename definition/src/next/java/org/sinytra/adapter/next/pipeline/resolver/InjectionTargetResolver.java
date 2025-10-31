package org.sinytra.adapter.next.pipeline.resolver;

import com.google.common.collect.Multimap;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.WeighedDisambiguation;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.AtData;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.env.param.MethodParameters;
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
import java.util.Objects;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_INVOKE;

public class InjectionTargetResolver implements Resolver {
    private static final int INSN_RANGE = 5;

    private final List<Resolver> subResolvers = new ArrayList<>();

    public void addSubResolver(Resolver subResolver) {
        this.subResolvers.add(subResolver);
    }

    @Override
    public TxResult resolve(MixinData mixin, MixinContext context, Configuration clean, MutableConfiguration dirty, Recipe recipe) {
        if (dirty.getAtData() != null) return TxResult.PASS;

        MethodQualifier dirtyQualifier = dirty.getTargetMethod();
        if (dirtyQualifier == null) return TxResult.FAIL;

        MethodContext.TargetPair dirtyPair = context.methods().findOwnMethodPair(context.dirtyLookup(), dirtyQualifier);
        if (dirtyPair == null) return TxResult.FAIL;

        // Try reusing the original
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(dirtyPair);
        if (!insns.isEmpty()) {
            dirty.inheritAtData();
            return TxResult.SUCCESS;
        }

        for (Resolver subResolver : this.subResolvers) {
            TxResult result = subResolver.resolve(mixin, context, clean, dirty, recipe);
            if (result != TxResult.PASS) {
                return result;
            }
        }

        // Only support INVOKE for now
        if (!clean.getAtData().getValue().equals(AT_VAL_INVOKE)) {
            dirty.inheritAtData();
            return TxResult.SUCCESS;
        }

        // Find replacements
        if (findReplacedType(context, clean.getTargetMethod(), dirtyPair.methodNode(), dirtyPair, clean.getAtData(), dirty)) {
            return TxResult.SUCCESS;
        }

        return TxResult.FAIL;
    }

    private static boolean findReplacedType(MixinContext context, MethodQualifier cleanQualifier, MethodNode dirtyMethod, MethodContext.TargetPair dirtyPair, AtData original, MutableConfiguration dirty) {
        // Find single clean target minsn
        MethodContext.TargetPair cleanPair = context.methods().findOwnMethodPair(context.cleanLookup(), cleanQualifier);
        List<AbstractInsnNode> insns = context.methods().findInjectionTargetInsns(cleanPair);
        if (insns.isEmpty() || !(insns.getFirst() instanceof MethodInsnNode cleanInsn)) {
            return false;
        }

        InstructionMatcher cleanMatcher = MethodCallAnalyzer.findSurroundingInstructions(cleanInsn, INSN_RANGE);
        Multimap<String, MethodInsnNode> dirtyCalls = MethodCallAnalyzer.getMethodCalls(dirtyMethod, new ArrayList<>());
        List<InstructionMatcher> dirtyMatchers = dirtyCalls.values().stream()
            .map(i -> MethodCallAnalyzer.findSurroundingInstructions(i, INSN_RANGE))
            .toList();

        WeighedDisambiguation<MethodQualifier> magicBlackBox = WeighedDisambiguation.<MethodQualifier>builder()
            .match(() -> testMatchers(context, cleanInsn, cleanMatcher, dirtyMatchers, false))
            .match(() -> testMatchers(context, cleanInsn, cleanMatcher, dirtyMatchers, true))
            .match(() -> testOverloadedMethods(context, cleanInsn, cleanPair))
            .resultsEqual(MethodQualifier::equals)
            .build();

        MethodQualifier replacement = magicBlackBox.findBestMatch();
        if (replacement != null) {
            dirty.setAtData(original.withTarget(replacement.asDescriptor()));
            return true;
        }

        return false;
    }

    private static List<MethodQualifier> testMatchers(MixinContext context, MethodInsnNode cleanInsn, InstructionMatcher cleanMatcher, List<InstructionMatcher> dirtyMatchers, boolean partial) {
        return dirtyMatchers.stream()
            .map(m -> {
                boolean before = cleanMatcher.testBefore(m);
                boolean after = cleanMatcher.testAfter(m);
                boolean match = partial ? before || after : before && after;

                MethodInsnNode dirtyInsn = (MethodInsnNode) m.insn();
                if (match && matchesMethodCall(context, cleanInsn, dirtyInsn)) {
                    return dirtyInsn;
                }
                return null;
            })
            .filter(Objects::nonNull)
            .map(MethodQualifier::create)
            .toList();
    }

    private static List<MethodQualifier> testOverloadedMethods(MixinContext context, MethodInsnNode cleanInsn, MethodContext.TargetPair cleanPair) {
        ClassNode dirtyClass = context.dirtyLookup().getClass(cleanInsn.owner).orElse(null);
        if (dirtyClass == null) {
            return List.of();
        }

        List<Type> cleanParams = MethodParameters.getParameterTypes(cleanInsn.desc);
        List<MethodNode> methods = dirtyClass.methods.stream()
            .filter(m -> {
                if (cleanPair.classNode().methods.stream()
                    .noneMatch(c -> c.name.equals(m.name)
                        && c.desc.equals(m.desc)) && m.name.equals(cleanInsn.name)
                ) {
                    List<Type> dirtyParams = MethodParameters.getParameterTypes(m.desc);
                    return dirtyParams.size() > cleanParams.size() && dirtyParams.subList(0, cleanParams.size()).equals(cleanParams);
                }
                return false;
            })
            .toList();
        return methods.size() == 1 ? List.of(MethodQualifier.create(methods.getFirst())) : List.of();
    }

    private static boolean matchesMethodCall(MixinContext context, MethodInsnNode cleanInsn, MethodInsnNode dirtyInsn) {
        return cleanInsn.owner.equals(dirtyInsn.owner) && cleanInsn.name.equals(dirtyInsn.name)
            || context.getTypeAdapter(Type.getObjectType(dirtyInsn.owner), Type.getObjectType(cleanInsn.owner)) != null
            && Type.getArgumentTypes(cleanInsn.desc).length == Type.getArgumentTypes(dirtyInsn.desc).length;
    }
}
