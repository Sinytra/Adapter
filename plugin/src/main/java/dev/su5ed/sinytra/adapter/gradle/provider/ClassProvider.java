package dev.su5ed.sinytra.adapter.gradle.provider;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Optional;

public interface ClassProvider {
    Optional<ClassNode> getClass(String name);

    Optional<MethodNode> findMethod(String owner, String name, String desc);
}
