package org.sinytra.adapter.analysis.locals;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.method.MethodAnalyzer;

import java.util.*;

public class LocalVariableLookup {
    private final InsnList instructions;
    private final List<LocalVariableNode> sortedLocals;
    private final boolean isNonStatic;
    private final int paramCount;
    private final int lastParamLVTIndex;
    private final Int2ObjectMap<LocalVariableNode> byIndex = new Int2ObjectOpenHashMap<>();
    private final Map<Type, List<LocalVariableNode>> byType = new HashMap<>();

    @Nullable
    public static LocalVariableLookup of(MethodNode method) {
        return method.localVariables != null ? new LocalVariableLookup(method) : null;
    }

    public LocalVariableLookup(MethodNode methodNode) {
        this.instructions = methodNode.instructions;
        this.isNonStatic = !MethodAnalyzer.isStatic(methodNode);

        List<LocalVariableNode> localVariables = methodNode.localVariables;
        this.sortedLocals = localVariables == null ? List.of() : localVariables.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingInt(lvn -> lvn.index))
            .toList();
        for (LocalVariableNode node : this.sortedLocals) {
            this.byIndex.put(node.index, node);
        }

        Type[] params = Type.getArgumentTypes(methodNode.desc);
        this.paramCount = params.length;

        int lastParamIndex = this.isNonStatic ? 1 : 0;
        for (int i = 0; i < params.length - 1; i++) {
            lastParamIndex += params[i].getSize();
        }
        this.lastParamLVTIndex = params.length == 0 ? -1 : lastParamIndex;
    }

    public boolean isEmpty() {
        return this.sortedLocals.isEmpty();
    }

    public int size() {
        return this.sortedLocals.size();
    }

    public List<LocalVariableNode> getLocals() {
        return this.sortedLocals;
    }

    @Nullable
    public LocalVariableNode getByOrdinalOrNull(int ordinal) {
        return ordinal >= 0 && ordinal < this.sortedLocals.size() ? this.sortedLocals.get(ordinal) : null;
    }

    @Nullable
    public LocalVariableNode getByParameterOrdinalOrNull(int ordinal) {
        return ordinal >= 0 && ordinal < this.paramCount ? getByOrdinalOrNull(this.isNonStatic ? ordinal + 1 : ordinal) : null;
    }

    @Nullable
    public LocalVariableNode getByIndexOrNull(int index) {
        return this.byIndex.get(index);
    }

    @Nullable
    public LocalVariableNode getInScopeOrNull(int index, AbstractInsnNode insn) {
        int position = this.instructions.indexOf(insn);
        for (LocalVariableNode node : this.sortedLocals) {
            if (node.index == index && this.instructions.indexOf(node.start) <= position && position < this.instructions.indexOf(node.end)) {
                return node;
            }
        }
        return null;
    }

    public int getOrdinal(LocalVariableNode node) {
        return this.sortedLocals.indexOf(node);
    }

    public int getParameterOrdinal(LocalVariableNode node) {
        if (node.index > this.lastParamLVTIndex) {
            return -1;
        }
        int ordinal = getOrdinal(node);
        if (ordinal == -1) {
            return -1;
        }
        int paramOrdinal = ordinal - (this.isNonStatic ? 1 : 0);
        return paramOrdinal >= 0 && paramOrdinal < this.paramCount ? paramOrdinal : -1;
    }

    public List<LocalVariableNode> getForType(LocalVariableNode node) {
        return getForType(Type.getType(node.desc));
    }

    public List<LocalVariableNode> getForType(Type type) {
        return this.byType.computeIfAbsent(type, t -> this.sortedLocals.stream()
            .filter(l -> Type.getType(l.desc).equals(type))
            .toList());
    }
}
