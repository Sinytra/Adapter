package org.sinytra.adapter.analysis;

import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.util.provider.ClassLookup;

import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

public class InheritanceHandler {
    private final ClassLookup classProvider;
    // ConcurrentHashMap: one handler instance is shared by every mixin using the same class lookup, and the
    // transformation is driven from a ForkJoinPool.
    private final Map<String, Collection<String>> parentCache = new ConcurrentHashMap<>();

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

    public Collection<String> getClassParents(String name) {
        Collection<String> parents = this.parentCache.get(name);
        if (parents != null) {
            return parents;
        }
        // Not computeIfAbsent: computeClassParents recurses into getClassParents, which it rejects.
        Collection<String> computed = computeClassParents(name);
        Collection<String> prev = this.parentCache.putIfAbsent(name, computed);
        return prev != null ? prev : computed;
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

