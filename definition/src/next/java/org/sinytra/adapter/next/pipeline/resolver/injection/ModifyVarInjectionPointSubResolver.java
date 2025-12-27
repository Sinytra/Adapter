package org.sinytra.adapter.next.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.env.MixinContext;
import org.sinytra.adapter.next.env.ann.MixinData;
import org.sinytra.adapter.next.env.ann.ModifyVariableMixinData;
import org.sinytra.adapter.next.pipeline.Recipe;
import org.sinytra.adapter.next.pipeline.config.Configuration;
import org.sinytra.adapter.next.pipeline.resolver.SubResolver;
import org.sinytra.adapter.patch.analysis.InstructionMatcher;
import org.sinytra.adapter.patch.analysis.locals.LocalVarAnalyzer;
import org.sinytra.adapter.patch.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.patch.api.MethodContext;

import java.util.List;

import static org.sinytra.adapter.next.env.ann.MixinAnnotationConstants.AT_VAL_STORE;
import static org.sinytra.adapter.next.pipeline.config.Configuration.Keys.ORDINAL;

public class ModifyVarInjectionPointSubResolver implements SubResolver {

    @Nullable
    @Override
    public Configuration resolve(MixinData mixin, MixinContext context, Configuration clean, Configuration dirty, Recipe recipe) {
        if (!(mixin instanceof ModifyVariableMixinData mvdata) || !clean.getAtData().getValue().equals(AT_VAL_STORE)) {
            return null;
        }
        MethodContext.TargetPair pair = context.methods().findOwnMethodPair(context.dirtyLookup(), dirty.getTargetMethod());
        // Find replacement
        return findComparableReplacement(mvdata, context, clean, pair, dirty);
    }

    private static Configuration findComparableReplacement(ModifyVariableMixinData mixin, MixinContext context, Configuration clean, MethodContext.TargetPair dirtyPair, Configuration dirty) {
        if (mixin.ordinal().isEmpty()) return null;

        int ordinal = mixin.ordinal().getAsInt();
        Type varType = Type.getReturnType(context.methodNode().desc);

        MethodContext.TargetPair cleanPair = context.methods().findOwnMethodPair(context.cleanLookup(), clean.getTargetMethod());
        LocalVariableLookup lookup = new LocalVariableLookup(cleanPair.methodNode());
        LocalVariableNode desired = lookup.getByTypedOrdinal(varType, ordinal).orElseThrow();

        // Find variable initializer insns
        InsnList desiredInitializerInsns = LocalVarAnalyzer.findInitializerInsns(cleanPair.methodNode(), desired.index);

        // Get all matching variables
        for (MethodNode method : dirtyPair.classNode().methods) {
            LocalVariableLookup dirtyLookup = new LocalVariableLookup(method);
            List<LocalVariableNode> lvs = method.localVariables.stream()
                .filter(lvn -> desired.desc.equals(lvn.desc))
                .filter(lvn -> {
                    InsnList insns = LocalVarAnalyzer.findInitializerInsns(method, lvn.index);
                    return InstructionMatcher.test(desiredInitializerInsns, insns);
                })
                .toList();
            if (lvs.size() == 1) {
                int dirtyOrdinal = dirtyLookup.getOrdinal(lvs.getFirst());

                return dirty.subConfig()
                    .setProperty(ORDINAL, dirtyOrdinal)
                    .setTargetMethod(method)
                    .inheritAtData();
            }
        }
        return null;
    }
}
