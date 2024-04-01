package org.sinytra.adapter.patch.analysis.params;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;

import java.util.List;
import java.util.function.Consumer;

@CanIgnoreReturnValue
public interface ParamsDiffSnapshotBuilder {
    ParamsDiffSnapshotBuilder insert(int index, Type type);

    ParamsDiffSnapshotBuilder insertions(List<Pair<Integer, Type>> insertions);

    ParamsDiffSnapshotBuilder replace(int index, Type type);

    ParamsDiffSnapshotBuilder replacements(List<Pair<Integer, Type>> replacements);

    ParamsDiffSnapshotBuilder swap(int from, int to);

    ParamsDiffSnapshotBuilder swaps(List<Pair<Integer, Integer>> swaps);

    ParamsDiffSnapshotBuilder substitute(int target, int substitute);

    ParamsDiffSnapshotBuilder substitutes(List<Pair<Integer, Integer>> substitutes);

    ParamsDiffSnapshotBuilder move(int from, int to);

    ParamsDiffSnapshotBuilder moves(List<Pair<Integer, Integer>> moves);

    ParamsDiffSnapshotBuilder remove(int index);

    ParamsDiffSnapshotBuilder removals(List<Integer> removals);

    ParamsDiffSnapshotBuilder inline(int target, Consumer<InstructionAdapter> adapter);

    ParamsDiffSnapshotBuilder inlines(List<Pair<Integer, Consumer<InstructionAdapter>>> inlines);

    ParamsDiffSnapshotBuilder merge(SimpleParamsDiffSnapshot diff);

    ParamsDiffSnapshotBuilder merge(SimpleParamsDiffSnapshot diff, int indexOffset);

    @CheckReturnValue
    List<Integer> getRemovals();
}
