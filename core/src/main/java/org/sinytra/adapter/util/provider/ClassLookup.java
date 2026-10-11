package org.sinytra.adapter.util.provider;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.util.AdapterUtil;

import java.util.Optional;

public interface ClassLookup {
    Optional<ClassNode> getClass(String name);

    default Optional<MethodNode> findMethod(String owner, String name, String desc) {
        return getClass(owner).map(cls -> AdapterUtil.findMethod(cls, name, desc));
    }

    default Optional<FieldNode> findField(String owner, String name) {
        return getClass(owner).stream()
            .flatMap(cls -> cls.fields.stream())
            .filter(fd -> fd.name.equals(name))
            .findFirst();
    }
}
