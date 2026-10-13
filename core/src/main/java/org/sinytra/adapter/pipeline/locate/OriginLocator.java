package org.sinytra.adapter.pipeline.locate;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.ctx.MixinContext;

import java.util.Comparator;
import java.util.List;

public final class OriginLocator {
    public record Origin(TargetPair method, boolean matches, double similarity, boolean deprecated) {
    }

    private static final Comparator<Origin> RANKING = Comparator.comparing(Origin::matches).reversed()
        .thenComparing(Comparator.comparingDouble(Origin::similarity).reversed())
        .thenComparing(Origin::deprecated)
        .thenComparing(Comparator.<Origin>comparingInt(o -> Parameters.getParameterTypes(o.method().methodNode().desc).size()).reversed());

    public static List<Origin> locate(MixinContext context, TargetPair cleanTarget, String targetClass, MethodQualifier cleanQualifier) {
        List<TargetPair> candidates;
        if (MethodAnalyzer.isLambda(cleanQualifier)) {
            ClassNode dirtyClass = context.dirtyLookup().getClass(targetClass).orElse(null);
            candidates = dirtyClass == null ? List.of() : dirtyClass.methods.stream()
                .filter(method -> MethodAnalyzer.isLambda(method) && method.desc.equals(cleanTarget.methodNode().desc))
                .map(method -> new TargetPair(dirtyClass, method))
                .toList();
        } else {
            candidates = context.dirtyMethods().findTargetsByName(cleanQualifier);
        }
        return candidates.stream()
            .map(candidate -> new Origin(
                candidate,
                context.injections().matches(candidate),
                MethodAlignment.align(cleanTarget.methodNode(), candidate.methodNode()).similarity(),
                isDirtyDeprecated(context, candidate.methodNode())
            ))
            .sorted(RANKING)
            .toList();
    }

    private static boolean isDirtyDeprecated(MixinContext context, MethodNode dirty) {
        TargetPair clean = context.cleanMethods().resolve(MethodQualifier.create(dirty));
        return clean != null ? MethodAnalyzer.isDirtyDeprecatedMethod(clean.methodNode(), dirty) : MethodAnalyzer.isDeprecated(dirty);
    }

    private OriginLocator() {
    }
}
