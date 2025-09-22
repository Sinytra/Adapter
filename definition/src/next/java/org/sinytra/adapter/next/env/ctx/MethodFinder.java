package org.sinytra.adapter.next.env.ctx;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext.TargetPair;
import org.sinytra.adapter.patch.util.MethodQualifier;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.slf4j.Logger;

import java.util.List;

public class MethodFinder {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static class Flags {
        public static final int IGNORE_DESC = 0b01;
        public static final int FALLBACK_OWNER = 0b10;
    }

    @Nullable
    private final String fallbackOwner;

    public MethodFinder(String fallbackOwner) {
        this.fallbackOwner = fallbackOwner;
    }

    @Nullable
    public TargetPair findMethod(ClassLookup lookup, MethodQualifier qualifier, int flags) {
        Pair<ClassNode, List<MethodNode>> pair = findMethods(lookup, qualifier, flags);
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
    public Pair<ClassNode, List<MethodNode>> findMethods(ClassLookup lookup, MethodQualifier qualifier, int flags) {
        if (qualifier == null || qualifier.name() == null) {
            return null;
        }

        // Determine target class
        String owner;
        if (qualifier.internalOwnerName() != null) {
            owner = qualifier.internalOwnerName();
        } else if (hasFlag(flags, Flags.FALLBACK_OWNER) && this.fallbackOwner != null) {
            owner = this.fallbackOwner;
        } else {
            return null;
        }

        // Find target class
        ClassNode targetClass = lookup.getClass(owner).orElse(null);
        if (targetClass == null) {
            return null;
        }

        // Find target method in class
        String desc = qualifier.desc();
        List<MethodNode> candidates = targetClass.methods.stream()
            .filter(mtd -> mtd.name.equals(qualifier.name())
                && (hasFlag(flags, Flags.IGNORE_DESC) || desc == null || mtd.desc.equals(desc)))
            .toList();

        // If there's multiple candidates, try removing bouncer methods
        if (candidates.size() > 1 && desc == null) {
            candidates = candidates.stream().filter(mtd -> (mtd.access & Opcodes.ACC_SYNTHETIC) == 0 && (mtd.access & Opcodes.ACC_BRIDGE) == 0).toList();
        }

        if (candidates.isEmpty() && targetClass.superName != null) {
            return findMethods(lookup, new MethodQualifier(Type.getObjectType(targetClass.superName).getDescriptor(), qualifier.name(), qualifier.desc()), flags);
        }

        return Pair.of(targetClass, candidates);
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
