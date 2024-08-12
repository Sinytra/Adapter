package org.sinytra.adapter.patch.analysis.params;

import com.mojang.datafixers.util.Pair;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.transformer.operation.param.ParamTransformTarget;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public interface ParamsDiffSnapshot {
    enum Flags {
        UPGRADE_WRAP_OP;
    } 
    
    boolean isEmpty();

    List<Pair<Integer, Type>> insertions();

    List<Pair<Integer, Type>> replacements();

    List<Integer> removals();
    
    ParamsDiffSnapshot offset(int offset, int limit);

    default MethodTransform asParameterTransformer(ParamTransformTarget type, boolean withOffset) {
        return asParameterTransformer(type, withOffset, EnumSet.of(Flags.UPGRADE_WRAP_OP));
    }

    MethodTransform asParameterTransformer(ParamTransformTarget type, boolean withOffset, Set<Flags> flags);
}
