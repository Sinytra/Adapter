package org.sinytra.adapter.env.ctx;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public record TargetPair(ClassNode classNode, MethodNode methodNode) {
}
