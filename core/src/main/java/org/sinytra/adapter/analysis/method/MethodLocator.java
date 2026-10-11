package org.sinytra.adapter.analysis.method;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.ClassLookup;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.model.MethodQualifier;
import org.sinytra.adapter.model.TargetPair;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public final class MethodLocator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final PatchEnvironment environment;
    private final ClassLookup lookup;
    @Nullable
    private final String defaultOwner;

    public MethodLocator(PatchEnvironment environment, ClassLookup lookup, @Nullable String defaultOwner) {
        this.environment = environment;
        this.lookup = lookup;
        this.defaultOwner = defaultOwner;
    }

    public ClassLookup lookup() {
        return this.lookup;
    }

    /// {@return a single method matching the qualifier, searched in the specified class or default owner}
    @Nullable
    public TargetPair resolve(MethodQualifier qualifier) {
        return resolveSingle(qualifier, true);
    }

    /// {@return the single method matching the qualifier in the class it specifies}
    @Nullable
    public TargetPair resolveDeclared(MethodQualifier qualifier) {
        return resolveSingle(qualifier, false);
    }

    /// {@return the first method matching the qualifier in the class it specifies or its parents}
    @Nullable
    public TargetPair resolveInherited(MethodQualifier qualifier) {
        String owner = qualifier.internalOwnerName();
        if (owner == null || qualifier.name() == null) return null;

        ClassNode node = this.lookup.getClass(owner).orElse(null);
        if (node == null) return null;

        Collection<String> parents = this.environment.inheritanceHandler(this.lookup).getClassParents(node.name);

        return Stream.concat(Stream.of(node.name), parents.stream())
            .flatMap(cls -> this.lookup.findMethod(cls, qualifier.name(), qualifier.desc()).stream()
                .map(m -> new TargetPair(this.lookup.getClass(cls).orElseThrow(), m)))
            .findFirst()
            .orElse(null);
    }

    /// {@return all methods with the qualifier's name, searched in the specified class or default owner}
    public List<MethodNode> findByName(MethodQualifier qualifier) {
        Pair<ClassNode, List<MethodNode>> pair = findCandidates(qualifier, true, true);
        return pair != null ? pair.getSecond() : List.of();
    }

    @Nullable
    private TargetPair resolveSingle(MethodQualifier qualifier, boolean useDefaultOwner) {
        Pair<ClassNode, List<MethodNode>> pair = findCandidates(qualifier, useDefaultOwner, false);
        if (pair == null) {
            return null;
        }

        if (pair.getSecond().isEmpty()) {
            LOGGER.debug("Target method not found: {}{}{}", qualifier.owner(), qualifier.name(), qualifier.desc());
            return null;
        } else if (pair.getSecond().size() > 1) {
            LOGGER.debug("Multiple candidates found for method: {}{}{}", qualifier.owner(), qualifier.name(), qualifier.desc());
            return null;
        }

        return new TargetPair(pair.getFirst(), pair.getSecond().getFirst());
    }

    @Nullable
    private Pair<ClassNode, List<MethodNode>> findCandidates(@Nullable MethodQualifier qualifier, boolean useDefaultOwner, boolean ignoreDesc) {
        if (qualifier == null || qualifier.name() == null) {
            return null;
        }

        // Determine target class
        String owner;
        if (qualifier.internalOwnerName() != null) {
            owner = qualifier.internalOwnerName();
        } else if (useDefaultOwner && this.defaultOwner != null) {
            owner = this.defaultOwner;
        } else {
            return null;
        }

        // Find target class
        ClassNode targetClass = this.lookup.getClass(owner).orElse(null);
        if (targetClass == null) {
            return null;
        }

        // Find target method in class
        String desc = qualifier.desc();
        List<MethodNode> candidates = targetClass.methods.stream()
            .filter(mtd -> mtd.name.equals(qualifier.name()) && (ignoreDesc || desc == null || mtd.desc.equals(desc)))
            .toList();

        // If there's multiple candidates, try removing bouncer methods
        if (candidates.size() > 1 && desc == null) {
            candidates = candidates.stream().filter(mtd -> (mtd.access & Opcodes.ACC_SYNTHETIC) == 0 && (mtd.access & Opcodes.ACC_BRIDGE) == 0).toList();
        }

        if (candidates.isEmpty() && targetClass.superName != null) {
            return findCandidates(new MethodQualifier(Type.getObjectType(targetClass.superName).getDescriptor(), qualifier.name(), qualifier.desc()), useDefaultOwner, ignoreDesc);
        }

        return Pair.of(targetClass, candidates);
    }
}
