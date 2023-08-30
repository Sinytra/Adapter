package dev.su5ed.sinytra.adapter.patch;

import org.objectweb.asm.tree.ClassNode;
import dev.su5ed.sinytra.adapter.patch.Patch.Result;

public interface ClassTransform {
    Result apply(ClassNode classNode);
}
