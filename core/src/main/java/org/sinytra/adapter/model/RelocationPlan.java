package org.sinytra.adapter.model;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;

public sealed interface RelocationPlan {
    record Move(List<MethodNode> lambdas, List<FieldNode> widenedFields, List<MethodNode> widenedMethods) implements RelocationPlan {
    }

    record Mirror(ClassNode handlerOwner, boolean isStatic, @Nullable ValueSource receiver, List<ValueSource> params) implements RelocationPlan {
    }
}
