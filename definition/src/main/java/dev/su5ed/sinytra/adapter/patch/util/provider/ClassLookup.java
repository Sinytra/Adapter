package dev.su5ed.sinytra.adapter.patch.util.provider;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Optional;

public interface ClassLookup {
    Optional<ClassNode> getClass(String name);

    default Optional<MethodNode> findMethod(String owner, String name, String desc) {
        return getClass(owner).stream()
            .flatMap(cls -> cls.methods.stream())
            .filter(mtd -> mtd.name.equals(name) && mtd.desc.equals(desc))
            .findFirst();
    }
}
