package dev.su5ed.sinytra.adapter.patch.analysis;

import dev.su5ed.sinytra.adapter.patch.util.provider.ClassLookup;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class InheritanceHandler {
    private final ClassLookup classProvider;
    private final Map<String, Collection<String>> parentCache = new HashMap<>();

    public InheritanceHandler(ClassLookup classProvider) {
        this.classProvider = classProvider;
    }

    public boolean isClassInherited(String child, String parent) {
        if (child.equals(parent)) {
            return true;
        }
        ClassNode childNode = this.classProvider.getClass(child).orElse(null);
        ClassNode parentNode = this.classProvider.getClass(parent).orElse(null);
        return childNode != null && parentNode != null && getClassParents(child).contains(parent);
    }

    public boolean isMethodOverriden(String cls, String name, String desc) {
        for (String parent : getClassParents(cls)) {
            ClassNode node = this.classProvider.getClass(parent).orElse(null);
            if (node != null) {
                for (MethodNode method : node.methods) {
                    if (method.name.equals(name) && method.desc.equals(desc) && (method.access & Opcodes.ACC_PRIVATE) == 0 && (method.access & Opcodes.ACC_FINAL) == 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Collection<String> getClassParents(String name) {
        Collection<String> parents = this.parentCache.get(name);
        if (parents == null) {
            parents = computeClassParents(name);
            this.parentCache.put(name, parents);
        }
        return parents;
    }

    private Collection<String> computeClassParents(String name) {
        ClassNode node = this.classProvider.getClass(name).orElse(null);
        Set<String> parents = new HashSet<>();
        if (node != null) {
            if (node.superName != null) {
                parents.add(node.superName);
                if (!node.superName.equals("java/lang/Object")) {
                    parents.addAll(getClassParents(node.superName));
                }
            }
            if (node.interfaces != null) {
                parents.addAll(node.interfaces);
                for (String itf : node.interfaces) {
                    parents.addAll(getClassParents(itf));
                }
            }
        }
        return parents;
    }
}
