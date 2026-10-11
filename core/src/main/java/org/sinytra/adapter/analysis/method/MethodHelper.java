package org.sinytra.adapter.analysis.method;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.locals.LocalVariable;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.analysis.params.ParamDiffResolver;
import org.sinytra.adapter.mixin.MockMixinRuntime;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Stream;

import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;

public class MethodHelper {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MixinContext context;

    public MethodHelper(MixinContext context) {
        this.context = context;
    }

    public List<Type> resolveCapturedMethodParams(Configuration clean, Configuration dirty) {
        List<Type> cleanCaptured = clean.getParameters().getTypes(CAPTURED_PARAMS);
        List<Type> dirtyCaptured = new ArrayList<>();

        // Evaluate parameter difference, capture additional params when necessary
        if (!cleanCaptured.isEmpty()) {
            TargetPair cleanPair = this.context.cleanMethods().resolve(clean.getTargetMethod());
            TargetPair dirtyPair = this.context.dirtyMethods().resolve(dirty.getTargetMethod());
            if (cleanPair == null || dirtyPair == null) return dirtyCaptured;
            MethodNode cleanTarget = cleanPair.methodNode();
            MethodNode dirtyTarget = dirtyPair.methodNode();

            LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.compareMethodParameters(cleanTarget, dirtyTarget);
            if (diff == null) return dirtyCaptured;

            List<Type> cleanTargetParams = Parameters.getParameterTypes(cleanTarget.desc);
            // Expect cleanTargetParams to begin with or be equal to cleanCaptured
            if (cleanCaptured.size() > cleanTargetParams.size()) return dirtyCaptured;
            // Use a sublist instead of cleanCaptured to be able to compare Type instances directly
            List<Type> capturedSublist = cleanTargetParams.subList(0, cleanCaptured.size());
            if (!cleanCaptured.equals(capturedSublist)) return dirtyCaptured;

            ParamDiffResolver.ParamEvalResult evalResult = ParamDiffResolver.resolve(cleanTargetParams, diff);

            int maxIndex = capturedSublist.stream()
                .map(evalResult::getUpdated)
                .filter(Objects::nonNull)
                .mapToInt(ParamDiffResolver.ParamState::dirtyIndex)
                .max()
                .orElse(-1);

            if (maxIndex != -1) {
                List<Type> dirtyTargetParams = Parameters.getParameterTypes(dirtyTarget.desc);
                dirtyCaptured = List.copyOf(dirtyTargetParams.subList(0, maxIndex + 1));
            }
        }

        return dirtyCaptured;
    }

    @Nullable
    public List<LocalVariable> getTargetMethodLocals(TargetPair target) {
        Type[] targetParams = Type.getArgumentTypes(target.methodNode().desc);
        boolean isStatic = isStatic(target.methodNode());
        int lvtOffset = isStatic ? 0 : 1;
        // The starting LVT index is of the first var after all method parameters. Offset by 1 for instance methods to skip 'this'
        int targetLocalPos = targetParams.length + lvtOffset;
        return getTargetMethodLocals(target, targetLocalPos);
    }

    @Nullable
    public List<LocalVariable> getTargetMethodLocals(TargetPair target, int startPos) {
        return getTargetMethodLocals(target, startPos, this.context.environment().fabricLVTCompatibility());
    }

    @Nullable
    public List<LocalVariable> getTargetMethodLocals(TargetPair target, int startPos, int lvtCompatLevel) {
        List<AbstractInsnNode> targetInsns = this.context.injections().find(target);
        if (targetInsns.isEmpty()) {
            LOGGER.debug("Skipping LVT patch, no target instructions found");
            return null;
        }
        // Get available local variables at the injection point in the target method
        LocalVariableNode[] localVariables = MockMixinRuntime.getLocalsAt(target, targetInsns.getFirst(), lvtCompatLevel);
        LocalVariable[] locals = Stream.of(localVariables)
            .filter(Objects::nonNull)
            .map(lv -> new LocalVariable(lv.index, Type.getType(lv.desc)))
            .toArray(LocalVariable[]::new);
        return AdapterUtil.summariseLocals(locals, startPos);
    }

    @Nullable
    public LocalVariableLookup getLVT(MethodNode method) {
        if (method.localVariables == null) {
            return null;
        }
        return new LocalVariableLookup(method);
    }

    public static boolean isStatic(MethodNode node) {
        return (node.access & Opcodes.ACC_STATIC) != 0;
    }
}
