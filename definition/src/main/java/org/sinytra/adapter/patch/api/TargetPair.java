package org.sinytra.adapter.patch.api;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public record TargetPair(ClassNode classNode, MethodNode methodNode) {
}
