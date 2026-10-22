package org.sinytra.adapter.model;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public sealed interface RelocationPlan {
    record Move(List<MethodNode> lambdas, List<FieldNode> widenedFields, List<MethodNode> widenedMethods, boolean makeStatic) implements RelocationPlan {
    }

    record Mirror(ClassNode handlerOwner, boolean isStatic, @Nullable ValueSource receiver, List<ValueSource> params, @Nullable Guard guard) implements RelocationPlan {
    }

    /// Limits a mirror to calls that come from the original method, through the given call
    record Guard(Relocation.OriginCall via, Passthrough passthrough) {
    }

    /// What a guarded mirror returns when it doesn't run the handler
    enum Passthrough {
        /// Nothing, the handler returns void
        SKIP,
        /// The value the handler would modify, its first parameter
        KEEP_VALUE,
        /// True, so the wrapped instruction still runs
        PROCEED,
        /// The result of the wrapped operation, called with the operands the handler received before it
        CALL_ORIGINAL
    }
}
