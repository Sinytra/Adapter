package org.sinytra.adapter.pipeline.processor;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.analysis.method.InheritanceHandler;
import org.sinytra.adapter.edit.param.ParamTransformationUtil;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.TxResult;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.Describer;
import org.sinytra.adapter.pipeline.locate.LocalLocator;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MixinBridge;

import java.util.ArrayList;
import java.util.List;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_ARGS_ONLY;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_INDEX;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.PROPERTY_ORDINAL;

public class SugarLocalProcessor implements Processor {
    @Override
    public TxResult process(MixinContext context, Configuration dirty, Recipe recipe) {
        TargetPair cleanTarget = recipe.getCleanTarget();
        TargetPair dirtyTarget = recipe.getDirtyTarget();
        AtData dirtyAt = dirty.getAtData();
        if (cleanTarget == null || dirtyTarget == null || dirtyAt == null) {
            return TxResult.PASS;
        }

        MethodNode cleanHandler = context.unmodifiedMethodNode();
        MethodNode handler = context.methodNode();
        List<Integer> cleanLocals = findLocalParameters(cleanHandler);
        List<Integer> locals = findLocalParameters(handler);
        if (locals.isEmpty() || cleanLocals.size() != locals.size()) {
            return TxResult.PASS;
        }
        AbstractInsnNode cleanInsn = AdapterUtil.getFirstOrNull(context.injections().find(cleanTarget));
        AbstractInsnNode dirtyInsn = AdapterUtil.getFirstOrNull(context.injections().find(dirtyTarget, InjectionQuery.of(dirtyAt)));
        if (cleanInsn == null || dirtyInsn == null) {
            return TxResult.PASS;
        }

        String mixin = context.classNode().name;
        MixinBridge.Session cleanSession = MixinBridge.session(mixin, cleanHandler, cleanTarget.classNode(), cleanTarget.methodNode(), context.environment());
        MixinBridge.Session dirtySession = MixinBridge.session(mixin, handler, dirtyTarget.classNode(), dirtyTarget.methodNode(), context.environment());
        TargetPair origin = recipe.getNewCleanTarget();
        boolean sameMethod = origin != null && origin.isSameMethod(dirtyTarget);
        boolean changed = false;
        for (int i = 0; i < locals.size(); i++) {
            int cleanIndex = cleanLocals.get(i);
            int index = locals.get(i);
            AnnotationNode cleanSelector = getLocalAnnotation(cleanHandler, cleanIndex);
            AnnotationNode selector = getLocalAnnotation(handler, index);
            Type cleanType = Type.getArgumentTypes(cleanHandler.desc)[cleanIndex];
            Type type = Type.getArgumentTypes(handler.desc)[index];

            int current = dirtySession.findLocal(selector, type, dirtyInsn);
            if (current != -1 && sameMethod) {
                continue;
            }
            int cleanSlot = cleanSession.findLocal(cleanSelector, cleanType, cleanInsn);
            if (cleanSlot == -1) {
                continue;
            }
            int slot = LocalLocator.resolve(context, recipe, cleanTarget, cleanSlot, cleanInsn, dirtyTarget);
            if (slot == -1 || current == slot) {
                continue;
            }

            Type dirtyType = getLocalType(dirtyTarget.methodNode(), slot, dirtyInsn);
            Boolean widened = dirtyType == null ? null : compareTypes(context, type, dirtyType);
            if (widened == null) {
                context.recordCtxAudit("Can't select local %s for @Local parameter %s of type %s", slot, index, type);
                continue;
            }
            AnnotationNode newSelector = describeSelector(context, dirtySession, selector, dirtyTarget, dirtyInsn, slot, dirtyType);
            if (newSelector == null) {
                context.recordCtxAudit("Can't select local %s for @Local parameter %s", slot, index);
                continue;
            }

            selector.values = newSelector.values;
            if (!dirtyType.equals(type)) {
                ParamTransformationUtil.retypeParameter(handler, index, dirtyType, widened);
            }
            updateModel(dirty.getParameters(), handler, index);
            context.recordCtxAudit("Select local %s of type %s for @Local parameter %s", slot, dirtyType, index);
            changed = true;
        }
        return changed ? TxResult.SUCCESS : TxResult.PASS;
    }

    private static List<Integer> findLocalParameters(MethodNode method) {
        List<Integer> indices = new ArrayList<>();
        int count = Type.getArgumentCount(method.desc);
        for (int i = 0; i < count; i++) {
            if (getLocalAnnotation(method, i) != null) {
                indices.add(i);
            }
        }
        return indices;
    }

    @Nullable
    public static AnnotationNode getLocalAnnotation(MethodNode method, int index) {
        for (List<AnnotationNode>[] annotations : List.of(
            method.invisibleParameterAnnotations != null ? method.invisibleParameterAnnotations : new List[0],
            method.visibleParameterAnnotations != null ? method.visibleParameterAnnotations : new List[0]
        )) {
            AnnotationNode local = index < annotations.length ? AdapterUtil.getAnnotation(annotations[index], MixinAnnotations.LOCAL) : null;
            if (local != null) {
                return local;
            }
        }
        return null;
    }

    @Nullable
    private static Type getLocalType(MethodNode method, int slot, AbstractInsnNode insn) {
        LocalVariableLookup lookup = LocalVariableLookup.of(method);
        LocalVariableNode local = lookup != null ? lookup.getInScopeOrNull(slot, insn) : null;
        if (local == null && lookup != null && Describer.isParameter(method, slot)) {
            local = lookup.getLocals().stream().filter(l -> l.index == slot).findFirst().orElse(null);
        }
        return local != null ? Type.getType(local.desc) : null;
    }

    @Nullable
    private static Boolean compareTypes(MixinContext context, Type type, Type dirtyType) {
        if (type.equals(dirtyType)) {
            return false;
        }
        if (type.getSort() != Type.OBJECT || dirtyType.getSort() != Type.OBJECT) {
            return null;
        }
        InheritanceHandler inheritance = context.environment().inheritanceHandler(context.dirtyLookup());
        if (inheritance.isClassInherited(dirtyType.getInternalName(), type.getInternalName())) {
            return false;
        }
        if (inheritance.isClassInherited(type.getInternalName(), dirtyType.getInternalName())) {
            return true;
        }
        return null;
    }

    @Nullable
    private static AnnotationNode describeSelector(MixinContext context, MixinBridge.Session session, AnnotationNode selector, TargetPair target,
                                                   AbstractInsnNode insn, int slot, Type type) {
        boolean argsOnly = Boolean.TRUE.equals(AdapterUtil.getAnnotationValue(selector, PROPERTY_ARGS_ONLY)) && Describer.isParameter(target.methodNode(), slot);
        AnnotationNode adjusted = new AnnotationNode(selector.desc);
        if (argsOnly) {
            adjusted.visit(PROPERTY_ARGS_ONLY, true);
        }
        if (AdapterUtil.getAnnotationValue(selector, PROPERTY_INDEX) != null) {
            adjusted.visit(PROPERTY_INDEX, slot);
            return session.findLocal(adjusted, type, insn) == slot ? adjusted : null;
        }
        if (AdapterUtil.getAnnotationValue(selector, PROPERTY_ORDINAL) == null && session.findLocal(adjusted, type, insn) == slot) {
            return adjusted;
        }
        int ordinal = argsOnly
            ? Describer.getParameterOrdinal(target.methodNode(), slot, type)
            : context.locals().resolveLocalOrdinal(target, insn, type, slot);
        if (ordinal != -1) {
            adjusted.visit(PROPERTY_ORDINAL, ordinal);
            if (session.findLocal(adjusted, type, insn) == slot) {
                return adjusted;
            }
        }
        return null;
    }

    private static void updateModel(@Nullable MethodParameters params, MethodNode handler, int index) {
        if (params == null) {
            return;
        }
        Parameter parameter = Parameters.parse(handler, index, index + 1).getFirst();
        int offset = 0;
        for (MethodParameters.ParamGroup group : params.getOrder()) {
            List<Parameter> members = params.get(group);
            if (index < offset + members.size()) {
                members.set(index - offset, parameter);
                return;
            }
            offset += members.size();
        }
    }
}
