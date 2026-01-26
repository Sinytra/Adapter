package org.sinytra.adapter.analysis.params;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.transform.param.TransformParameters;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface ParamsDiffSnapshot {
    enum Flags {
        REMOVED_VAR_GRAVE
    }

    boolean isEmpty();

    List<Pair<Integer, Type>> insertions();

    List<Pair<Integer, Type>> replacements();

    List<Integer> removals();

    List<Pair<Integer, Integer>> swaps();

    List<Pair<Integer, Integer>> substitutes();

    List<Pair<Integer, Integer>> moves();

    List<Pair<Integer, Consumer<InstructionAdapter>>> inlines();

    ParamsDiffSnapshot offset(int offset);

    ParamsDiffSnapshot offset(int offset, int limit);

    TransformParameters asParameterTransformer(boolean withOffset, Set<Flags> flags);
}
