package org.sinytra.adapter.pipeline.locate;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.injection.InjectionQuery;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.ann.AtData;
import org.sinytra.adapter.model.ann.SliceData;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.MutableConfiguration;
import org.sinytra.adapter.model.config.key.IntentKeys;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.param.RedirectedInsn;
import org.sinytra.adapter.pipeline.Recipe;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.injector.MixinFlag;
import org.sinytra.adapter.util.OpcodeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_INVOKE_ASSIGN;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_LOAD;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_NEW;
import static org.sinytra.adapter.model.constant.MixinAnnotationConstants.AT_VAL_STORE;

public final class Describer {
    @Nullable
    public static Configuration describe(MixinContext context, Recipe recipe, Candidate candidate) {
        AtData cleanAt = recipe.clean().getAtData();
        TargetPair origin = recipe.getDirtyTarget();
        boolean otherMethod = origin == null || !candidate.target().isSameMethod(origin);
        Function<AtData, InjectionQuery> query = otherMethod ? at -> InjectionQuery.of(at).ignoringSlice() : InjectionQuery::of;
        AtData described = cleanAt != null ? describeAt(context, cleanAt, candidate, query) : null;
        if (described == null) {
            return null;
        }
        MutableConfiguration config = MutableConfiguration.create().setAtData(described);
        if (otherMethod && recipe.clean().getProperty(MixinKeys.SLICES).map(slices -> !slices.isEmpty()).orElse(false)) {
            config.setProperty(MixinKeys.SLICES, List.of());
        }
        if (otherMethod && recipe.clean().hasProperty(MixinKeys.SLICE)) {
            config.setProperty(MixinKeys.SLICE, new SliceData(null, null, null));
        }

        if (isLocalAt(context, described) && candidate.insn() instanceof VarInsnNode var) {
            AbstractInsnNode position = OpcodeUtil.isStoreOpcode(var.getOpcode()) ? var.getNext() : var;
            MutableConfiguration local = describeLocal(context, recipe, candidate.target(), position, var.var);
            if (local == null) {
                return null;
            }
            config.mergeFrom(local);
        }

        if (otherMethod) {
            config.setTargetMethod(candidate.target().methodNode());
            String targetClass = candidate.target().classNode().name;
            if (!targetClass.equals(recipe.dirty().getTargetClass())) {
                config.setTargetClass(targetClass);
                if (candidate.relocation() != null) {
                    config.setProperty(IntentKeys.RELOCATION, candidate.relocation());
                }
            }
        }
        return config;
    }

    @Nullable
    public static AtData describeAt(MixinContext context, AtData cleanAt, Candidate candidate, Function<AtData, InjectionQuery> query) {
        if (isLocalAt(context, cleanAt)) {
            if (!(candidate.insn() instanceof VarInsnNode var)) {
                return null;
            }
            List<VarInsnNode> uses = new ArrayList<>();
            for (AbstractInsnNode insn : candidate.target().methodNode().instructions) {
                if (insn instanceof VarInsnNode use && use.getOpcode() == var.getOpcode() && use.var == var.var) {
                    uses.add(use);
                }
            }
            return cleanAt.getOrdinal().isPresent() ? cleanAt.withOrdinal(uses.indexOf(var)) : cleanAt;
        }

        boolean changesKind = candidate.insn() instanceof MethodInsnNode && !AT_VAL_INVOKE.equals(cleanAt.getValue()) && !AT_VAL_INVOKE_ASSIGN.equals(cleanAt.getValue());
        AtData at;
        if (changesKind) {
            at = AtData.create(AT_VAL_INVOKE, (MethodInsnNode) candidate.insn());
        } else if (candidate.insn() instanceof TypeInsnNode creation && AT_VAL_NEW.equals(cleanAt.getValue()) && cleanAt.getTarget().isPresent()) {
            at = retargetCreation(cleanAt, creation);
            if (at == null) {
                return null;
            }
        } else {
            at = candidate.insn() instanceof MethodInsnNode call && cleanAt.getTarget().isPresent() ? cleanAt.withTarget(call) : cleanAt;
        }
        List<AbstractInsnNode> selected = context.injections().find(candidate.target(), query.apply(at.withOrdinal(null)));
        int ordinal = selected.indexOf(candidate.insn());
        if (ordinal == -1) {
            return null;
        }
        boolean keepsOrdinal = changesKind ? selected.size() > 1 : cleanAt.getOrdinal().isPresent();
        return keepsOrdinal ? at.withOrdinal(ordinal) : at.withOrdinal(null);
    }

    @Nullable
    public static MutableConfiguration describeLocal(MixinContext context, Recipe recipe, TargetPair target, AbstractInsnNode insn, int slot) {
        Configuration clean = recipe.clean();
        Type type = Type.getReturnType(context.methodNode().desc);
        MutableConfiguration config = MutableConfiguration.create();

        boolean argsOnly = clean.getProperty(MixinKeys.ARGS_ONLY).orElse(false);
        if (argsOnly && !isParameter(target.methodNode(), slot)) {
            argsOnly = false;
            config.setProperty(MixinKeys.ARGS_ONLY, false);
        }

        if (clean.hasProperty(MixinKeys.INDEX)) {
            return config.setProperty(MixinKeys.INDEX, slot);
        }
        if (clean.hasProperty(MixinKeys.ORDINAL)) {
            int ordinal = argsOnly ? getParameterOrdinal(target.methodNode(), slot, type) : context.locals().resolveLocalOrdinal(target, insn, type, slot);
            return ordinal != -1 ? config.setProperty(MixinKeys.ORDINAL, ordinal) : null;
        }
        return config;
    }

    @Nullable
    private static AtData retargetCreation(AtData cleanAt, TypeInsnNode creation) {
        String target = cleanAt.getTargetOrThrow();
        boolean descriptor = target.startsWith("(");
        if (!descriptor && !target.contains("<init>")) {
            return cleanAt;
        }
        MethodInsnNode constructor = RedirectedInsn.findConstructorCall(creation);
        if (constructor == null) {
            return null;
        }
        Type owner = Type.getObjectType(creation.desc);
        return cleanAt.withTarget(descriptor
            ? Type.getMethodDescriptor(owner, Type.getArgumentTypes(constructor.desc))
            : owner.getDescriptor() + "<init>" + constructor.desc);
    }

    public static List<AbstractInsnNode> findSelectable(MixinContext context, TargetPair target, AtData at, Function<AtData, InjectionQuery> query) {
        if (!isLocalAt(context, at)) {
            return context.injections().find(target, query.apply(at.withOrdinal(null)));
        }
        int opcode = Type.getReturnType(context.methodNode().desc).getOpcode(AT_VAL_STORE.equals(at.getValue()) ? Opcodes.ISTORE : Opcodes.ILOAD);
        List<AbstractInsnNode> uses = new ArrayList<>();
        for (AbstractInsnNode insn : target.methodNode().instructions) {
            if (insn.getOpcode() == opcode) {
                uses.add(insn);
            }
        }
        return uses;
    }

    public static AbstractInsnNode toLocatable(MixinContext context, AtData at, AbstractInsnNode selected) {
        if (isLocalAt(context, at) && AT_VAL_STORE.equals(at.getValue()) && !OpcodeUtil.isStoreOpcode(selected.getOpcode())) {
            AbstractInsnNode store = InsnTraversal.find(selected, InsnTraversal.BACKWARD, insn -> insn.getOpcode() >= 0);
            if (store != null && OpcodeUtil.isStoreOpcode(store.getOpcode())) {
                return store;
            }
        }
        return selected;
    }

    public static boolean isSame(Configuration a, Configuration b) {
        return Objects.equals(a.getAtData(), b.getAtData())
            && Objects.equals(a.getTargetMethod(), b.getTargetMethod())
            && Objects.equals(a.getTargetClass(), b.getTargetClass())
            && Objects.equals(a.getProperty(MixinKeys.ORDINAL), b.getProperty(MixinKeys.ORDINAL))
            && Objects.equals(a.getProperty(MixinKeys.INDEX), b.getProperty(MixinKeys.INDEX));
    }

    public static boolean isLocalAt(MixinContext context, AtData at) {
        return context.hasFlag(MixinFlag.TARGETS_VARIABLE) && (AT_VAL_STORE.equals(at.getValue()) || AT_VAL_LOAD.equals(at.getValue()));
    }

    public static boolean isParameter(MethodNode method, int slot) {
        return slot < (MethodAnalyzer.isStatic(method) ? 0 : 1) + Arrays.stream(Type.getArgumentTypes(method.desc)).mapToInt(Type::getSize).sum();
    }

    public static int getParameterOrdinal(MethodNode method, int slot, Type type) {
        int current = MethodAnalyzer.isStatic(method) ? 0 : 1;
        int ordinal = 0;
        for (Type arg : Type.getArgumentTypes(method.desc)) {
            if (current == slot) {
                return arg.equals(type) ? ordinal : -1;
            }
            if (arg.equals(type)) {
                ordinal++;
            }
            current += arg.getSize();
        }
        return -1;
    }

    private Describer() {
    }
}
