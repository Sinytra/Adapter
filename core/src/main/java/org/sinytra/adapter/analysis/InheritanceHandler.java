package org.sinytra.adapter.analysis;

import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.util.provider.ClassLookup;

import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

public class InheritanceHandler {
    private final ClassLookup classProvider;
    // ConcurrentHashMap, not HashMap: ONE handler instance is shared by every mixin that uses the same class
    // lookup (PatchEnvironmentImpl caches them with putIfAbsent), while ART drives the transformation from a
    // ForkJoinPool. Concurrent put() on a plain HashMap corrupts its internal table, so get() could return
    // null or a wrong entry - which made inheritance lookups (and therefore "does this target method exist")
    // depend on thread interleaving. That is the non-determinism behind onBlockBroken/onExplosionDone
    // sometimes being adapted and sometimes being stripped from the same build. See 12-issue草案.md entry 13.
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
        // get/compute/putIfAbsent rather than computeIfAbsent: computeClassParents recurses into
        // getClassParents, which ConcurrentHashMap.computeIfAbsent rejects as a recursive update.
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

