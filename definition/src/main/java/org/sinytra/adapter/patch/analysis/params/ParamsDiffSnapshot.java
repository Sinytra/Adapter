package org.sinytra.adapter.patch.analysis.params;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;

import java.util.List;

public interface ParamsDiffSnapshot {
    boolean isEmpty();

    List<Pair<Integer, Type>> insertions();

    List<Pair<Integer, Type>> replacements();

    List<Integer> removals();
    
    ParamsDiffSnapshot offset(int offset, int limit);

    List<MethodTransform> asParameterTransformer(ParamTransformTarget type, boolean withOffset);
}
