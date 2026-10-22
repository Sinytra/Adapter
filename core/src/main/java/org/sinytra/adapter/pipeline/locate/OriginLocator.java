package org.sinytra.adapter.pipeline.locate;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.compare.MethodAlignment;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.sinytra.adapter.model.param.Parameters;
import org.sinytra.adapter.pipeline.ctx.MixinContext;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.Comparator;
import java.util.List;

public final class OriginLocator {
    /// @param piece a new method that took over part of a removed target
    public record Origin(TargetPair method, boolean matches, MethodAlignment alignment, boolean deprecated, boolean piece) {
        public double similarity() {
            return this.alignment.similarity();
        }
    }

    private static final double MIN_PIECE_COVERAGE = 0.5;
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
            if (candidates.isEmpty()) {
                return findPieces(context, cleanTarget);
            }
        }
        return candidates.stream()
            .map(candidate -> new Origin(
                candidate,
                context.injections().matches(candidate),
                MethodAlignment.align(cleanTarget.methodNode(), candidate.methodNode()),
                isDirtyDeprecated(context, candidate.methodNode()),
                false
            ))
            .sorted(RANKING)
            .toList();
    }

    private static List<Origin> findPieces(MixinContext context, TargetPair cleanTarget) {
        ClassNode cleanClass = cleanTarget.classNode();
        ClassNode dirtyClass = context.dirtyLookup().getClass(cleanClass.name).orElse(null);
        if (dirtyClass == null) {
            return List.of();
        }
        boolean isStatic = MethodAnalyzer.isStatic(cleanTarget.methodNode());
        return dirtyClass.methods.stream()
            .filter(method -> !MethodAnalyzer.isLambda(method) && MethodAnalyzer.isStatic(method) == isStatic
                && AdapterUtil.findMethod(cleanClass, method.name, method.desc) == null)
            .map(method -> {
                TargetPair candidate = new TargetPair(dirtyClass, method);
                MethodAlignment alignment = MethodAlignment.align(cleanTarget.methodNode(), method);
                return new Origin(candidate, context.injections().matches(candidate), alignment, MethodAnalyzer.isDeprecated(method), true);
            })
            .filter(origin -> origin.alignment().dirtyCoverage() >= MIN_PIECE_COVERAGE)
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
