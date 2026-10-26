package org.sinytra.adapter.pipeline.transform;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariable;
import org.sinytra.adapter.analysis.locals.LocalsAnalyzer;
import org.sinytra.adapter.api.AuditTrail;
import org.sinytra.adapter.api.MethodPatchContext;
import org.sinytra.adapter.api.MethodTransformer;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.model.Targets;
import org.sinytra.adapter.model.config.Configuration;
import org.sinytra.adapter.model.config.key.MixinKeys;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.pipeline.locate.Describer;
import org.sinytra.adapter.pipeline.locate.LocalLocator;
import org.sinytra.adapter.util.MixinBridge;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalCaptureUpgradeTransformer implements MethodTransformer {
    @Override
    public PatchResult apply(MethodPatchContext patchContext) {
        MixinContext context = (MixinContext) patchContext;
        return apply(context, context.configuration());
    }

    public PatchResult apply(MixinContext context, Configuration config) {
        MethodNode handler = context.methodNode();
        Targets targets = context.targets(config.getTargetMethod());
        if (targets == null || !config.hasProperty(MixinKeys.LOCALS) || Parameters.parse(handler).stream().anyMatch(p -> p.hasAnnotation(MixinAnnotations.LOCAL))) {
            return PatchResult.PASS;
        }

        LocalsAnalyzer.CapturedLocalsInfo info = context.locals().resolveCapturedLocalsInfo(targets.dirty());
        if (info == null || info.diff().isEmpty()) {
            return PatchResult.PASS;
        }

        AbstractInsnNode cleanInsn = context.injections().resolveFirst(targets.clean());
        AbstractInsnNode dirtyInsn = context.injections().resolveFirst(targets.dirty());
        List<LocalVariable> cleanLocals = context.locals().resolveAvailableLocals(targets.clean());
        if (cleanInsn == null || dirtyInsn == null || cleanLocals == null) {
            return PatchResult.PASS;
        }

        LocalsAnalyzer.CapturedLocals captured = info.capturedLocals();
        LocalsAnalyzer.CapturedLocalsTransform transform = LocalsAnalyzer.analyzeCapturedLocals(captured, handler);
        MixinBridge.Session dirtySession = MixinBridge.session(context.classNode().name, handler, targets.dirty().classNode(), targets.dirty().methodNode(), context.environment());
        Type[] params = Type.getArgumentTypes(handler.desc);
        Map<Integer, AnnotationNode> selectors = new HashMap<>();

        for (int param : transform.used()) {
            Type type = params[param];
            long rank = Arrays.stream(params, captured.paramLocalStart(), param).filter(type::equals).count();
            LocalVariable cleanLocal = cleanLocals.stream().filter(local -> local.type().equals(type)).skip(rank).findFirst().orElse(null);
            if (cleanLocal == null) {
                return PatchResult.PASS;
            }

            int slot = LocalLocator.resolve(context, targets.clean(), targets.dirty(), cleanLocal.index(), cleanInsn, targets.dirty());
            AnnotationNode selector = slot == -1 ? null : Describer.describeLocalSelector(context, dirtySession, new AnnotationNode(MixinAnnotations.LOCAL), targets.dirty(), dirtyInsn, slot, type);
            if (selector == null) {
                return PatchResult.PASS;
            }

            selectors.put(param, selector);
        }

        selectors.forEach((param, selector) -> selector.accept(handler.visitParameterAnnotation(param, MixinAnnotations.LOCAL, false)));
        PatchResult result = transform.remover().isEmpty() ? PatchResult.APPLY : transform.remover().apply(context);
        if (result == PatchResult.PASS) {
            return PatchResult.PASS;
        }

        context.methodAnnotation().setOrAppendNonNull(MixinKeys.TARGET_METHOD.name(), config.serialize(MixinKeys.TARGET_METHOD, config.getTargetMethod()));
        context.recordCtxAudit("Upgrade captured locals");
        context.environment().auditTrail().recordResult(context, config, AuditTrail.Match.FULL);
        return result;
    }
}
