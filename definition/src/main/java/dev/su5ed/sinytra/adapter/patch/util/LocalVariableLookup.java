package dev.su5ed.sinytra.adapter.patch.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.objectweb.asm.tree.LocalVariableNode;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class LocalVariableLookup {
    private final List<LocalVariableNode> sortedLocals;
    private final Int2ObjectMap<LocalVariableNode> byIndex = new Int2ObjectOpenHashMap<>();

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
}
