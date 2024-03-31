package org.sinytra.adapter.patch.analysis;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;

import java.util.Comparator;
import java.util.List;

public final class LocalVarRearrangement {
    public record IndexedType(Type type, int index) {
        public static IndexedType fromLocalVar(LocalVariableNode local) {
            return new IndexedType(Type.getType(local.desc), local.index);
        }
    }

    @Nullable
    public static Int2IntMap getRearrangedParametersFromLocals(List<LocalVariableNode> cleanLocals, List<LocalVariableNode> dirtyLocals) {
        return getRearrangedParameters(cleanLocals.stream().map(LocalVarRearrangement.IndexedType::fromLocalVar).toList(), dirtyLocals.stream().map(LocalVarRearrangement.IndexedType::fromLocalVar).toList());
    }

    @Nullable
    public static Int2IntMap getRearrangedParameters(List<IndexedType> parameterTypes, List<IndexedType> newParameterTypes) {
        List<IndexedType> sortedParameterTypes = parameterTypes.stream().sorted(Comparator.comparingInt(IndexedType::index)).toList();
        List<IndexedType> sortedNewParameterTypes = newParameterTypes.stream().sorted(Comparator.comparingInt(IndexedType::index)).toList();

        Object2IntMap<Type> typeCount = new Object2IntOpenHashMap<>();
        ListMultimap<Type, Integer> typeIndices = ArrayListMultimap.create();
        for (IndexedType local : sortedParameterTypes) {
            typeCount.put(local.type(), typeCount.getInt(local.type()) + 1);
            typeIndices.put(local.type(), local.index());
        }
        Object2IntMap<Type> newTypeCount = new Object2IntOpenHashMap<>();
        for (IndexedType local : sortedNewParameterTypes) {
            newTypeCount.put(local.type(), newTypeCount.getInt(local.type()) + 1);
        }

        for (Object2IntMap.Entry<Type> entry : typeCount.object2IntEntrySet()) {
            if (newTypeCount.getInt(entry.getKey()) != entry.getIntValue()) {
                return null;
            }
        }

        Object2IntMap<Type> seenTypes = new Object2IntOpenHashMap<>();
        Int2IntMap swaps = new Int2IntLinkedOpenHashMap();
        for (IndexedType local : sortedNewParameterTypes) {
            if (typeIndices.containsKey(local.type())) {
                List<Integer> indices = typeIndices.get(local.type());
                int seen = seenTypes.getInt(local.type());
                int oldIndex = indices.get(seen);
                seenTypes.put(local.type(), seen + 1);
                int index = local.index();
                if (oldIndex != index) {
                    swaps.put(oldIndex, index);
                }
            }
        }

        return swaps.isEmpty() ? null : swaps;
    }

    private LocalVarRearrangement() {}
}
