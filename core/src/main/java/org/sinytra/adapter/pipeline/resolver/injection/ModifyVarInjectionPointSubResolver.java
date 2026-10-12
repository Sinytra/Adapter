package org.sinytra.adapter.pipeline.resolver.injection;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.locals.LocalsAnalyzer;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.resolver.SubResolver;

import static org.sinytra.adapter.model.config.key.MixinKeys.ORDINAL;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_STORE;

public class ModifyVarInjectionPointSubResolver implements SubResolver {

    @Nullable
    @Override
    public Configuration resolve(MixinContext context, Recipe recipe) {
        if (!recipe.clean().getAtData().getValue().equals(AT_VAL_STORE)) return null;
        Targets targets = recipe.targets();
        if (targets == null) return null;

        // Find replacement
        Integer ordinal = recipe.clean().getProperty(ORDINAL).orElse(null);
        if (ordinal == null) return null;

        Type varType = Type.getReturnType(context.methodNode().desc);

        LocalVariableLookup lookup = recipe.cleanLocalsTable();
        if (lookup == null) return null;

        LocalVariableNode desired = lookup.getByTypedOrdinal(varType, ordinal).orElse(null);
        if (desired == null) return null;

        // Find variable initializer insns
        InsnList desiredInitializerInsns = LocalsAnalyzer.findInitializerInsns(targets.clean().methodNode(), desired.index);

        // Get all matching variables
        for (MethodNode method : targets.dirty().classNode().methods) {
            LocalVariableLookup dirtyLookup = LocalVariableLookup.of(method);
            if (dirtyLookup == null) continue;

            LocalVariableNode match = LocalsAnalyzer.findLocalWithInitializer(method, desired.desc, desiredInitializerInsns).orElse(null);
            if (match != null) {
                Integer dirtyOrdinal = dirtyLookup.getTypedOrdinal(match).orElse(null);
                if (dirtyOrdinal == null) continue;

                return recipe.dirty().copyClean()
                    .setProperty(ORDINAL, dirtyOrdinal)
                    .setTargetMethod(method)
                    .inheritAtData();
            }
        }
        return null;
    }
}
