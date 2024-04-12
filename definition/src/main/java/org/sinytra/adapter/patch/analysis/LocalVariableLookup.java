package org.sinytra.adapter.patch.analysis;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;

public class LocalVariableLookup {
    private final List<LocalVariableNode> sortedLocals;
    private final boolean isNonStatic;
    private final Int2ObjectMap<LocalVariableNode> byIndex = new Int2ObjectOpenHashMap<>();
    private final Map<Type, List<LocalVariableNode>> byType = new HashMap<>();

    public LocalVariableLookup(MethodNode methodNode) {
        this.isNonStatic = (methodNode.access & Opcodes.ACC_STATIC) == 0;
        this.sortedLocals = methodNode.localVariables.stream().sorted(Comparator.comparingInt(lvn -> lvn.index)).toList();
        for (LocalVariableNode node : this.sortedLocals) {
            this.byIndex.put(node.index, node);
        }
    }

    public LocalVariableNode getByOrdinal(int ordinal) {
        return this.sortedLocals.get(ordinal);
    }

    public LocalVariableNode getByParameterOrdinal(int ordinal) {
        return this.sortedLocals.get(this.isNonStatic ? ordinal + 1 : ordinal);
    }

    public LocalVariableNode getByIndex(int index) {
        return Objects.requireNonNull(this.byIndex.get(index), "Missing local variable at index " + index);
    }

    @Nullable
    public LocalVariableNode getByIndexOrNull(int index) {
        return this.byIndex.get(index);
    }

    public int getOrdinal(LocalVariableNode node) {
        return this.sortedLocals.indexOf(node);
    }

    public LocalVariableNode getLast() {
        return this.sortedLocals.get(this.sortedLocals.size() - 1);
    }

    public List<LocalVariableNode> getForType(LocalVariableNode node) {
        return getForType(Type.getType(node.desc));
    }

    public List<LocalVariableNode> getForType(Type type) {
        return this.byType.computeIfAbsent(type, t -> this.sortedLocals.stream()
            .filter(l -> Type.getType(l.desc).equals(type))
            .toList());
    }

    public Optional<LocalVariableNode> getByTypedOrdinal(Type type, int ordinal) {
        List<LocalVariableNode> available = getForType(type);
        return available.size() > ordinal ? Optional.of(available.get(ordinal)) : Optional.empty();
    }

    public Optional<Integer> getTypedOrdinal(LocalVariableNode node) {
        int ordinal = getForType(node).indexOf(node);
        return ordinal == -1 ? Optional.empty() : Optional.of(ordinal);
    }
}
