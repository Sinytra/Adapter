package org.sinytra.adapter.patch.transformer;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.sinytra.adapter.patch.util.SingleValueHandle;

import java.util.*;

public class LVTSnapshot {
    private final List<LocalVar> locals;
    private final int[] vars;

    public LVTSnapshot(List<LocalVar> locals, int[] vars) {
        this.locals = locals;
        this.vars = vars;
    }

    public void applyDifference(MethodNode newNode) {
        final LVTSnapshot newLVT = take(newNode);

        // A new local var was removed
        // Shift all other vars after it
        final List<LocalVar> removed = new ArrayList<>(this.locals);
        removed.removeIf(newLVT.locals::contains);
        int[] newVars = Arrays.copyOf(this.vars, this.vars.length);
        for (LocalVar local : removed) {
            for (int i = 0; i < newVars.length; i++) {
                if (newVars[i] > local.index) {
                    newVars[i] -= local.desc.getSize();
                }
            }
            newNode.localVariables.forEach(node -> {
                if (node.index > local.index) {
                    node.index -= local.desc.getSize();
                }
            });
        }

        // A new local var was added
        // Shift all other vars after it, including the one that was replaced
        final List<LocalVar> added = new ArrayList<>(newLVT.locals);
        added.removeIf(this.locals::contains);
        for (LocalVar local : added) {
            for (int i = 0; i < newVars.length; i++) {
                if (newVars[i] >= local.index) {
                    newVars[i] += local.desc.getSize();
                }
            }
            newNode.localVariables.forEach(node -> {
                if (!node.name.equals(local.name) && node.index >= local.index) {
                    node.index += local.desc.getSize();
                }
            });
        }

        final Int2IntMap old2New = new Int2IntArrayMap();
        for (int i = 0; i < this.vars.length; i++) {
            old2New.put(this.vars[i], newVars[i]);
        }

        newNode.instructions.forEach(insn -> {
            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
            if (handle != null) {
                final int idx = handle.get();
                handle.set(old2New.getOrDefault(idx, idx));
            }
        });
    }

    public static LVTSnapshot take(MethodNode node) {
        final List<LocalVar> locals = new ArrayList<>();
        final IntSet vars = new IntArraySet();
        node.localVariables.forEach(local -> locals.add(new LocalVar(local.name, Type.getType(local.desc), local.index)));
        node.instructions.forEach(insn -> {
            SingleValueHandle<Integer> handle = AdapterUtil.handleLocalVarInsnValue(insn);
            if (handle != null) {
                vars.add((int) handle.get());
            }
        });
        final int[] varsArray = vars.toIntArray();
        Arrays.sort(varsArray);
        Collections.sort(locals);
        return new LVTSnapshot(locals, varsArray);
    }

    public static void with(final MethodNode methodNode, final Runnable action) {
        final LVTSnapshot snapshot = take(methodNode);
        action.run();
        snapshot.applyDifference(methodNode);
    }

    public record LocalVar(String name, Type desc, int index) implements Comparable<LocalVar> {
        @Override
        public int compareTo(@NotNull LVTSnapshot.LocalVar o) {
            return Comparator.<Integer>naturalOrder().compare(this.index, o.index);
        }
    }
}
