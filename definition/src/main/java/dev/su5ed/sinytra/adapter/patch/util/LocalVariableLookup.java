package dev.su5ed.sinytra.adapter.patch.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;

import java.util.*;

public class LocalVariableLookup {
    private final List<LocalVariableNode> sortedLocals;
    private final Int2ObjectMap<LocalVariableNode> byIndex = new Int2ObjectOpenHashMap<>();
    private final Map<Type, List<LocalVariableNode>> byType = new HashMap<>();

    public LocalVariableLookup(List<LocalVariableNode> locals) {
        this.sortedLocals = locals.stream().sorted(Comparator.comparingInt(lvn -> lvn.index)).toList();
        for (LocalVariableNode node : this.sortedLocals) {
            this.byIndex.put(node.index, node);
        }
    }

    public LocalVariableNode getByOrdinal(int ordinal) {
        return this.sortedLocals.get(ordinal);
    }

    public LocalVariableNode getByIndex(int index) {
        return Objects.requireNonNull(this.byIndex.get(index), "Missing local variable at index " + index);
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

    public OptionalInt getTypedOrdinal(LocalVariableNode node) {
        int ordinal = getForType(node).indexOf(node);
        return ordinal == -1 ? OptionalInt.empty() : OptionalInt.of(ordinal);
    }
}
