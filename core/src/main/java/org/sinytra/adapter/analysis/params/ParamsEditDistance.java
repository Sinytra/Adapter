package org.sinytra.adapter.analysis.params;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.locals.LocalVariableLookup;
import org.sinytra.adapter.util.GeneratedVariables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Diffs two parameter lists by weighted edit distance with swaps (Damerau-Levenshtein distance)
 */
public final class ParamsEditDistance {
    private static final int INSERT = 100;
    private static final int DELETE = 100;
    private static final int SWAP = 100;
    private static final int CONVERTIBLE = 50;
    private static final int UNRELATED = 100;
    private static final int OTHER_NAME = 1;

    private enum Operation {
        MATCH,
        DELETE,
        INSERT,
        SWAP
    }

    private record Step(Operation op, int clean, int dirty, int swapClean, int swapDirty) {
        public Step(Operation op, int clean, int dirty) {
            this(op, clean, dirty, -1, -1);
        }
    }

    private record Table(int[][] costs, int[][][] swapFrom) {
    }

    /// Represents a temp working parameter
    /// @param target target dirty index or -1 if it's removed
    private record Slot(Type type, int target) {
    }

    public static LayeredParamsDiffSnapshot diff(List<Param> clean, List<Param> dirty, TypeRelation relation) {
        Table table = computeCosts(clean, dirty, relation);
        return toSnapshot(clean, dirty, backtrack(clean, dirty, relation, table));
    }

    public static LayeredParamsDiffSnapshot diffTypes(List<Type> clean, List<Type> dirty) {
        return diffTypes(clean, dirty, TypeRelation.STATIC);
    }

    public static LayeredParamsDiffSnapshot diffTypes(List<Type> clean, List<Type> dirty, TypeRelation relation) {
        return diff(Param.of(clean), Param.of(dirty), relation);
    }

    public static LayeredParamsDiffSnapshot diffParameters(MethodNode clean, MethodNode dirty, TypeRelation relation) {
        return diff(getParameters(clean), getParameters(dirty), relation);
    }

    private static List<Param> getParameters(MethodNode method) {
        LocalVariableLookup lookup = LocalVariableLookup.of(method);
        Type[] types = Type.getArgumentTypes(method.desc);
        List<Param> params = new ArrayList<>(types.length);

        for (int i = 0; i < types.length; i++) {
            LocalVariableNode local = lookup != null ? lookup.getByParameterOrdinalOrNull(i) : null;
            boolean stable = local != null && !GeneratedVariables.isPositionalName(local.name, types[i]);
            params.add(new Param(types[i], stable ? local.name : null));
        }

        return params;
    }

    private static Table computeCosts(List<Param> clean, List<Param> dirty, TypeRelation relation) {
        int cleanSize = clean.size();
        int dirtySize = dirty.size();

        int[][] costs = new int[cleanSize + 1][dirtySize + 1];
        int[][][] swapFrom = new int[cleanSize + 1][dirtySize + 1][];

        for (int i = 0; i <= cleanSize; i++) {
            for (int j = 0; j <= dirtySize; j++) {
                if (i == 0 || j == 0) {
                    costs[i][j] = i * DELETE + j * INSERT;
                    continue;
                }

                int cost = Math.min(costs[i - 1][j] + DELETE, costs[i][j - 1] + INSERT);
                cost = Math.min(cost, costs[i - 1][j - 1] + getMatchCost(clean.get(i - 1), dirty.get(j - 1), relation));

                for (int k = 1; k < i; k++) {
                    // Swapping the same types changes nothing
                    if (relation.relate(clean.get(k - 1).type(), clean.get(i - 1).type()) == TypeRelation.Kind.SAME) {
                        continue;
                    }

                    for (int l = 1; l < j; l++) {
                        int swap = getSwapCost(clean, dirty, relation, costs, i, j, k, l);
                        if (swap < cost) {
                            cost = swap;
                            swapFrom[i][j] = new int[]{k, l};
                        }
                    }
                }

                costs[i][j] = cost;
            }
        }
        return new Table(costs, swapFrom);
    }

    private static int getSwapCost(List<Param> clean, List<Param> dirty, TypeRelation relation, int[][] costs,
                                   int cleanLate, int dirtyLate, int cleanEarly, int dirtyEarly
    ) {
        // Turn the parameters before the swapped two into each other
        int before = costs[cleanEarly - 1][dirtyEarly - 1];
        // Delete the clean parameters between swapped params and insert the dirty ones
        int between = (cleanLate - cleanEarly - 1) * DELETE + (dirtyLate - dirtyEarly - 1) * INSERT;
        // The earlier clean parameter takes the later dirty position and the later one the earlier position
        int exchanged = getMatchCost(clean.get(cleanEarly - 1), dirty.get(dirtyLate - 1), relation)
            + getMatchCost(clean.get(cleanLate - 1), dirty.get(dirtyEarly - 1), relation);
        return before + between + exchanged + SWAP;
    }

    private static List<Step> backtrack(List<Param> clean, List<Param> dirty, TypeRelation relation, Table table) {
        int[][] costs = table.costs();
        List<Step> steps = new ArrayList<>();
        int i = clean.size();
        int j = dirty.size();

        while (i > 0 || j > 0) {
            int cost = costs[i][j];
            int[] swap = i > 0 && j > 0 ? table.swapFrom()[i][j] : null;

            if (j > 0 && cost == costs[i][j - 1] + INSERT) {
                steps.add(new Step(Operation.INSERT, -1, --j));
            } else if (i > 0 && cost == costs[i - 1][j] + DELETE) {
                steps.add(new Step(Operation.DELETE, --i, -1));
            } else if (swap != null && cost == getSwapCost(clean, dirty, relation, costs, i, j, swap[0], swap[1])) {
                int k = swap[0];
                int l = swap[1];

                // clean k - 1 goes to dirty j - 1
                // clean i - 1 to the dirty l - 1
                // the rest between them is replaced
                for (int insert = j - 2; insert >= l; insert--) {
                    steps.add(new Step(Operation.INSERT, -1, insert));
                }
                for (int delete = i - 2; delete >= k; delete--) {
                    steps.add(new Step(Operation.DELETE, delete, -1));
                }

                steps.add(new Step(Operation.SWAP, k - 1, j - 1, i - 1, l - 1));
                i = k - 1;
                j = l - 1;
            } else {
                steps.add(new Step(Operation.MATCH, --i, --j));
            }
        }

        return steps.reversed();
    }

    private static LayeredParamsDiffSnapshot toSnapshot(List<Param> clean, List<Param> dirty, List<Step> steps) {
        // Where each clean parameter goes
        int[] targets = new int[clean.size()];
        List<Integer> insertions = new ArrayList<>();

        for (Step step : steps) {
            switch (step.op()) {
                case MATCH -> targets[step.clean()] = step.dirty();
                case SWAP -> {
                    targets[step.clean()] = step.dirty();
                    targets[step.swapClean()] = step.swapDirty();
                }
                case DELETE -> targets[step.clean()] = -1;
                case INSERT -> insertions.add(step.dirty());
            }
        }

        // A deleted parameter inserted again elsewhere is a move
        List<Integer> moved = new ArrayList<>();
        for (Step step : steps) {
            if (step.op() == Operation.DELETE) {
                Type type = clean.get(step.clean()).type();
                insertions.stream()
                    .filter(j -> dirty.get(j).type().equals(type))
                    .findFirst()
                    .ifPresent(j -> {
                        insertions.remove(j);
                        targets[step.clean()] = j;
                        moved.add(step.clean());
                    });
            }
        }

        LayeredParamsDiffSnapshot.Builder builder = LayeredParamsDiffSnapshot.builder();
        List<Slot> state = new ArrayList<>();
        for (int i = 0; i < clean.size(); i++) {
            state.add(new Slot(clean.get(i).type(), targets[i]));
        }

        for (int index = state.size() - 1; index >= 0; index--) {
            if (state.get(index).target() == -1) {
                builder.remove(index);
                state.remove(index);
            }
        }

        for (int index = 0; index < state.size(); index++) {
            Slot slot = state.get(index);
            Type type = dirty.get(slot.target()).type();
            if (!slot.type().equals(type)) {
                builder.replace(index, type);
                state.set(index, new Slot(type, slot.target()));
            }
        }

        for (Step step : steps) {
            if (step.op() == Operation.SWAP) {
                int from = getIndexOfTarget(state, step.dirty());
                int to = getIndexOfTarget(state, step.swapDirty());
                builder.swap(from, to);
                state.set(from, state.set(to, state.get(from)));
            }
        }

        // Moved parameters that aren't placed yet may still be in the way
        // Place each after the last one that goes before it
        moved.sort(Comparator.comparingInt(i -> targets[i]));
        for (int cleanIndex : moved) {
            int from = getIndexOfTarget(state, targets[cleanIndex]);
            Slot slot = state.remove(from);
            int to = 0;
            for (int index = 0; index < state.size(); index++) {
                if (state.get(index).target() < slot.target()) {
                    to = index + 1;
                }
            }
            builder.move(from, to);
            state.add(to, slot);
        }

        Collections.sort(insertions);
        for (int j : insertions) {
            builder.insert(j, dirty.get(j).type());
            state.add(j, new Slot(dirty.get(j).type(), j));
        }
        return builder.build();
    }

    private static int getMatchCost(Param clean, Param dirty, TypeRelation relation) {
        int cost = switch (relation.relate(clean.type(), dirty.type())) {
            case SAME -> 0;
            case CONVERTIBLE -> CONVERTIBLE;
            case UNRELATED -> UNRELATED;
        };
        return clean.name() != null && dirty.name() != null && !clean.name().equals(dirty.name()) ? cost + OTHER_NAME : cost;
    }

    private static int getIndexOfTarget(List<Slot> state, int target) {
        for (int index = 0; index < state.size(); index++) {
            if (state.get(index).target() == target) {
                return index;
            }
        }
        throw new IllegalStateException("No parameter goes to " + target);
    }

    private ParamsEditDistance() {
    }
}
