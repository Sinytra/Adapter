package org.sinytra.adapter.analysis.locals;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.sinytra.adapter.analysis.InsnTraversal;
import org.sinytra.adapter.analysis.compare.InstructionMatcher;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.analysis.params.ParamDiffResolver;
import org.sinytra.adapter.analysis.params.ParamsEditDistance;
import org.sinytra.adapter.edit.param.TransformParameters;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.param.MethodParameters;
import org.sinytra.adapter.model.param.Parameter;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;
import org.sinytra.adapter.util.MixinBridge;
import org.sinytra.adapter.util.MixinBridge.LocalCandidate;
import org.sinytra.adapter.util.OpcodeUtil;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.sinytra.adapter.model.param.MethodParameters.ParamGroup.CAPTURED_PARAMS;

public final class LocalsAnalyzer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final MixinContext context;

    public LocalsAnalyzer(MixinContext context) {
        this.context = context;
    }

    public record CapturedLocals(TargetPair target, boolean isStatic, int paramLocalStart, int paramLocalEnd, int lvtOffset,
                                 List<Type> expected, LocalVariableLookup lvt) {
    }

    private record CapturedParameters(
        /// The index of the first captured parameter
        int start,
        List<Type> types
    ) {
    }

    @Nullable
    private static CapturedParameters findCapturedParameters(MethodNode methodNode) {
        List<Parameter> params = Parameters.parse(methodNode);
        for (int i = 0; i < params.size() - 1; i++) {
            if (MethodParameters.ParamGroup.CI_CIR.predicate().test(params.get(i))) {
                List<Type> types = params.subList(i + 1, params.size()).stream()
                    .takeWhile(p -> !p.isLocalOrShare())
                    .map(Parameter::type)
                    .toList();
                return new CapturedParameters(i + 1, types);
            }
        }
        return null;
    }

    public boolean capturesLocals() {
        CapturedParameters captured = findCapturedParameters(this.context.methodNode());
        return captured != null && !captured.types().isEmpty();
    }

    @Nullable
    public CapturedLocals resolveCapturedLocals(@Nullable TargetPair dirtyTarget) {
        if (dirtyTarget == null) {
            return null;
        }
        MethodNode methodNode = this.context.methodNode();
        CapturedParameters captured = findCapturedParameters(methodNode);
        if (captured == null) {
            LOGGER.debug("Missing CI or CIR argument in injector of type {}", this.context.methodAnnotation().getDesc());
            return null;
        }
        if (captured.types().isEmpty()) {
            return null;
        }
        LocalVariableLookup lvt = LocalVariableLookup.of(methodNode);
        if (lvt == null) {
            LOGGER.debug("Missing local variable table in injector {}", methodNode.name);
            return null;
        }
        boolean isStatic = MethodAnalyzer.isStatic(methodNode);
        int end = captured.start() + captured.types().size() - 1;
        return new CapturedLocals(dirtyTarget, isStatic, captured.start(), end, isStatic ? 0 : 1, captured.types(), lvt);
    }

    public record CapturedLocalsInfo(CapturedLocals capturedLocals, LayeredParamsDiffSnapshot diff) {
    }

    @Nullable
    public CapturedLocalsInfo resolveCapturedLocalsInfo(TargetPair dirtyTarget) {
        CapturedLocals capturedLocals = resolveCapturedLocals(dirtyTarget);
        if (capturedLocals == null) return null;

        // Get available local variables at the injection point in the target method
        List<LocalVariable> available = resolveAvailableLocals(capturedLocals.target());
        if (available == null) return null;

        List<Type> availableTypes = available.stream().map(LocalVariable::type).toList();
        // Compare expected and available params
        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(capturedLocals.expected(), availableTypes, this.context.typeRelation()).snapshot();

        return new CapturedLocalsInfo(capturedLocals, diff);
    }

    public static List<VarInsnNode> findStoreInsns(MethodNode methodNode, int index) {
        List<VarInsnNode> stores = new ArrayList<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn && varInsn.var == index && OpcodeUtil.isStoreOpcode(varInsn.getOpcode())) {
                stores.add(varInsn);
            }
        }
        return stores;
    }

    public static InsnList findInitializerInsns(MethodNode methodNode, int index) {
        return collectInitializerInsns(methodNode, index, load -> {
        });
    }

    public static Optional<LocalVariableNode> findLocalWithInitializer(MethodNode method, String desc, InsnList initializer) {
        if (method.localVariables == null) {
            return Optional.empty();
        }
        return AdapterUtil.findOne(method.localVariables, lvn -> desc.equals(lvn.desc)
            && InstructionMatcher.test(initializer, findInitializerInsns(method, lvn.index)));
    }

    private static InsnList collectInitializerInsns(MethodNode methodNode, int index, Consumer<VarInsnNode> onLoad) {
        InsnList insns = new InsnList();
        List<VarInsnNode> stores = findStoreInsns(methodNode, index);
        if (!stores.isEmpty()) {
            for (AbstractInsnNode prev : InsnTraversal.untilLabel(stores.getFirst(), InsnTraversal.BACKWARD)) {
                if (prev instanceof VarInsnNode load && OpcodeUtil.isLoadOpcode(load.getOpcode())) {
                    onLoad.accept(load);
                }
                insns.insert(prev.clone(Map.of()));
            }
        }
        return insns;
    }

    public record CapturedLocalsTransform(Collection<Integer> used, TransformParameters remover) {
    }

    public List<LocalCandidate> findSelectableLocals(TargetPair target, AbstractInsnNode insn, Type type) {
        return MixinBridge.session(this.context.classNode().name, this.context.methodNode(), target.classNode(), target.methodNode(), this.context.environment())
            .findLocalCandidates(type, insn, false);
    }

    public int resolveSelectedLocal(TargetPair target, AbstractInsnNode insn, Type type) {
        return MixinBridge.session(this.context.classNode().name, this.context.methodNode(), target.classNode(), target.methodNode(), this.context.environment())
            .findLocal(this.context.methodAnnotation().unwrap(), type, insn);
    }

    public int resolveLocalOrdinal(TargetPair target, AbstractInsnNode insn, Type type, int index) {
        return findSelectableLocals(target, insn, type).stream()
            .filter(c -> c.index() == index)
            .mapToInt(LocalCandidate::ordinal)
            .findFirst()
            .orElse(-1);
    }

    public int resolveLocalIndex(TargetPair target, AbstractInsnNode insn, Type type, int ordinal) {
        return findSelectableLocals(target, insn, type).stream()
            .filter(c -> c.ordinal() == ordinal)
            .mapToInt(LocalCandidate::index)
            .findFirst()
            .orElse(-1);
    }

    @Nullable
    public List<LocalVariable> resolveAvailableLocals(TargetPair target) {
        Type[] targetParams = Type.getArgumentTypes(target.methodNode().desc);
        int lvtOffset = MethodAnalyzer.isStatic(target.methodNode()) ? 0 : 1;
        int targetLocalPos = targetParams.length + lvtOffset;

        List<AbstractInsnNode> targetInsns = this.context.injections().find(target);
        if (targetInsns.isEmpty()) {
            LOGGER.debug("Skipping LVT patch, no target instructions found");
            return null;
        }

        LocalVariableNode[] localVariables = MixinBridge.getLocalsAt(target.classNode(), target.methodNode(), targetInsns.getFirst(), this.context.environment().fabricLVTCompatibility());
        LocalVariable[] locals = Stream.of(localVariables)
            .filter(Objects::nonNull)
            .map(lv -> new LocalVariable(lv.index, Type.getType(lv.desc)))
            .toArray(LocalVariable[]::new);
        return AdapterUtil.summariseLocals(locals, targetLocalPos);
    }

    public List<Type> findDirtyCapturedParams(Configuration clean, Configuration dirty) {
        List<Type> cleanCaptured = clean.getParameters().getTypes(CAPTURED_PARAMS);
        List<Type> dirtyCaptured = new ArrayList<>();

        if (!cleanCaptured.isEmpty()) {
            TargetPair cleanPair = this.context.cleanMethods().resolve(clean.getTargetMethod());
            TargetPair dirtyPair = this.context.dirtyMethods().resolve(dirty.getTargetMethod());
            if (cleanPair == null || dirtyPair == null) return dirtyCaptured;
            MethodNode cleanTarget = cleanPair.methodNode();
            MethodNode dirtyTarget = dirtyPair.methodNode();

            LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffParameters(cleanTarget, dirtyTarget, this.context.typeRelation()).snapshot();

            List<Type> cleanTargetParams = Parameters.getParameterTypes(cleanTarget.desc);
            if (cleanCaptured.size() > cleanTargetParams.size()) return dirtyCaptured;

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

    public static CapturedLocalsTransform analyzeCapturedLocals(CapturedLocals capturedLocals, MethodNode methodNode) {
        // Find used captured locals
        int paramLocalStart = capturedLocals.paramLocalStart();
        LocalVariableLookup table = capturedLocals.lvt();
        Set<Integer> used = new HashSet<>();
        for (AbstractInsnNode insn : methodNode.instructions) {
            if (insn instanceof VarInsnNode varInsn) {
                LocalVariableNode node = table.getByIndexOrNull(varInsn.var);
                if (node == null)
                    continue;
                int ordinal = table.getParameterOrdinal(node);
                if (ordinal == -1)
                    continue;
                if (ordinal >= paramLocalStart && ordinal <= capturedLocals.paramLocalEnd()) {
                    used.add(ordinal);
                }
            }
        }
        // Remove unused captured locals
        TransformParameters remover = TransformParameters.builder()
            .chain(b -> IntStream.range(paramLocalStart, capturedLocals.paramLocalEnd() + 1)
                .filter(i -> !used.contains(i))
                .boxed().sorted(Collections.reverseOrder())
                .forEach(b::remove))
            .build();
        return new CapturedLocalsTransform(used, remover);
    }
}
