package org.sinytra.adapter.patch.analysis.params;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CheckReturnValue;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.sinytra.adapter.patch.api.MethodTransform;
import org.sinytra.adapter.patch.transformer.ModifyMethodParams;
import org.sinytra.adapter.patch.transformer.param.InjectParameterTransform;
import org.sinytra.adapter.patch.transformer.param.ParamTransformTarget;
import org.sinytra.adapter.patch.transformer.param.ParameterTransformer;
import org.sinytra.adapter.patch.transformer.param.TransformParameters;
import org.sinytra.adapter.patch.util.AdapterUtil;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public record SimpleParamsDiffSnapshot(
    List<Pair<Integer, Type>> insertions,
    List<Pair<Integer, Type>> replacements,
    List<Pair<Integer, Integer>> swaps,
    List<Pair<Integer, Integer>> substitutes,
    List<Integer> removals,
    List<Pair<Integer, Integer>> moves,
    List<Pair<Integer, Consumer<InstructionAdapter>>> inlines
) implements ParamsDiffSnapshot {
    public static final Codec<Pair<Integer, Type>> MODIFICATION_CODEC = Codec.pair(
        Codec.INT.fieldOf("index").codec(),
        AdapterUtil.TYPE_CODEC.fieldOf("type").codec()
    );
    public static final Codec<Pair<Integer, Integer>> SWAP_CODEC = Codec.pair(
        Codec.INT.fieldOf("original").codec(),
        Codec.INT.fieldOf("replacement").codec()
    );
    public static final Codec<SimpleParamsDiffSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MODIFICATION_CODEC.listOf().optionalFieldOf("insertions", List.of()).forGetter(SimpleParamsDiffSnapshot::insertions),
        MODIFICATION_CODEC.listOf().optionalFieldOf("replacements", List.of()).forGetter(SimpleParamsDiffSnapshot::replacements),
        SWAP_CODEC.listOf().optionalFieldOf("swaps", List.of()).forGetter(SimpleParamsDiffSnapshot::swaps)
    ).apply(instance, (insertions, replacements, swaps) ->
        new SimpleParamsDiffSnapshot(insertions, replacements, swaps, List.of(), List.of(), List.of(), List.of())));

    public static SimpleParamsDiffSnapshot create(ParametersDiff diff) {
        return new SimpleParamsDiffSnapshot(diff.insertions(), diff.replacements(), diff.swaps(), List.of(), diff.removals(), diff.moves(), List.of());
    }

    public static SimpleParamsDiffSnapshot createLight(ParametersDiff diff) {
        return new SimpleParamsDiffSnapshot(List.of(), diff.replacements(), diff.swaps(), List.of(), diff.removals(), diff.moves(), List.of());
    }

    public boolean isEmpty() {
        return this.insertions.isEmpty()
            && this.replacements.isEmpty()
            && this.swaps.isEmpty()
            && this.substitutes.isEmpty()
            && this.removals.isEmpty()
            && this.moves.isEmpty()
            && this.inlines.isEmpty();
    }

    public boolean shouldComputeFrames() {
        return !this.swaps.isEmpty() || !this.replacements.isEmpty() || !this.substitutes.isEmpty() || !this.removals.isEmpty();
    }

    @Override
    public SimpleParamsDiffSnapshot offset(int offset, int limit) {
        UnaryOperator<Integer> offsetter = i -> i + offset;
        Predicate<Pair<Integer, ?>> limiter = p -> p.getFirst() < limit;

        return new SimpleParamsDiffSnapshot(
            this.insertions.stream().filter(limiter).map(p -> p.mapFirst(offsetter)).toList(),
            this.replacements.stream().filter(limiter).map(p -> p.mapFirst(offsetter)).toList(),
            this.swaps.stream().filter(limiter).map(p -> p.mapFirst(offsetter).mapSecond(offsetter)).toList(),
            this.substitutes.stream().filter(limiter).map(p -> p.mapFirst(offsetter).mapSecond(offsetter)).toList(),
            this.removals.stream().filter(i -> i < limit).map(offsetter).toList(),
            this.moves.stream().filter(limiter).map(p -> p.mapFirst(offsetter).mapSecond(offsetter)).toList(),
            this.inlines.stream().filter(limiter).map(p -> p.mapFirst(offsetter)).toList()
        );
    }

    @Override
    public List<MethodTransform> asParameterTransformer(ParamTransformTarget type, boolean withOffset) {
        List<MethodTransform> list = new ArrayList<>();
        SimpleParamsDiffSnapshot light = new SimpleParamsDiffSnapshot(List.of(), this.replacements, this.swaps, this.substitutes, this.removals, this.moves, this.inlines);
        if (!light.isEmpty()) {
            list.add(new ModifyMethodParams(light, type, false, null));
        }
        if (!this.insertions.isEmpty()) {
            list.add(new TransformParameters(this.insertions.stream()
                .<ParameterTransformer>map(p -> new InjectParameterTransform(p.getFirst(), p.getSecond()))
                .toList(), true, type));
        }
        return list;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements ParamsDiffSnapshotBuilder {
        private static final boolean DEBUG = Boolean.getBoolean("adapter.definition.paramdiff.debug");
        private static final Logger LOGGER = LogUtils.getLogger();

        private final List<Pair<Integer, Type>> insertions = new ArrayList<>();
        private final ImmutableList.Builder<Pair<Integer, Type>> replacements = ImmutableList.builder();
        private final ImmutableList.Builder<Pair<Integer, Integer>> swaps = ImmutableList.builder();
        private final ImmutableList.Builder<Pair<Integer, Integer>> substitutes = ImmutableList.builder();
        private final ImmutableList.Builder<Integer> removals = ImmutableList.builder();
        private final ImmutableList.Builder<Pair<Integer, Integer>> moves = ImmutableList.builder();
        private final ImmutableList.Builder<Pair<Integer, Consumer<InstructionAdapter>>> inlines = ImmutableList.builder();

        @Override
        public Builder insert(int index, Type type) {
            if (DEBUG) {
                LOGGER.info("Inserting {} at {}", type, index);
            }
            this.insertions.add(Pair.of(index, type));
            return this;
        }

        @Override
        public Builder insertions(List<Pair<Integer, Type>> insertions) {
            this.insertions.addAll(insertions);
            return this;
        }

        @Override
        public Builder replace(int index, Type type) {
            this.replacements.add(Pair.of(index, type));
            return this;
        }

        @Override
        public ParamsDiffSnapshotBuilder replacements(List<Pair<Integer, Type>> replacements) {
            this.replacements.addAll(replacements);
            return this;
        }

        @Override
        public Builder swap(int from, int to) {
            this.swaps.add(Pair.of(from, to));
            return this;
        }

        @Override
        public Builder swaps(List<Pair<Integer, Integer>> swaps) {
            this.swaps.addAll(swaps);
            return this;
        }

        @Override
        public ParamsDiffSnapshotBuilder substitute(int target, int substitute) {
            this.substitutes.add(Pair.of(target, substitute));
            return this;
        }

        @Override
        public ParamsDiffSnapshotBuilder substitutes(List<Pair<Integer, Integer>> substitutes) {
            this.substitutes.addAll(substitutes);
            return this;
        }

        @Override
        public Builder move(int from, int to) {
            this.moves.add(Pair.of(from, to));
            return this;
        }

        @Override
        public ParamsDiffSnapshotBuilder moves(List<Pair<Integer, Integer>> moves) {
            this.moves.addAll(moves);
            return this;
        }

        @Override
        public Builder remove(int index) {
            if (DEBUG) {
                LOGGER.info("Removing param at {}", index);
            }
            this.removals.add(index);
            return this;
        }

        @Override
        public ParamsDiffSnapshotBuilder removals(List<Integer> removals) {
            this.removals.addAll(removals);
            return this;
        }

        @Override
        public ParamsDiffSnapshotBuilder inline(int target, Consumer<InstructionAdapter> adapter) {
            this.inlines.add(Pair.of(target, adapter));
            return this;
        }

        @Override
        public ParamsDiffSnapshotBuilder inlines(List<Pair<Integer, Consumer<InstructionAdapter>>> inlines) {
            this.inlines.addAll(inlines);
            return this;
        }

        @Override
        public Builder merge(SimpleParamsDiffSnapshot diff) {
            return merge(diff, 0);
        }

        @Override
        public Builder merge(SimpleParamsDiffSnapshot diff, int indexOffset) {
            UnaryOperator<Integer> offsetter = i -> i + indexOffset;

            this.insertions.addAll(diff.insertions().stream().map(p -> p.mapFirst(offsetter)).toList());
            this.replacements.addAll(diff.replacements().stream().map(p -> p.mapFirst(offsetter)).toList());
            this.swaps.addAll(diff.swaps().stream().map(p -> p.mapFirst(offsetter).mapSecond(offsetter)).toList());
            this.substitutes.addAll(diff.substitutes().stream().map(p -> p.mapFirst(offsetter).mapSecond(offsetter)).toList());
            this.removals.addAll(diff.removals().stream().map(offsetter).toList());
            this.moves.addAll(diff.moves().stream().map(p -> p.mapFirst(offsetter).mapSecond(offsetter)).toList());
            this.inlines.addAll(diff.inlines().stream().map(p -> p.mapFirst(offsetter)).toList());

            return this;
        }

        @Override
        public List<Integer> getRemovals() {
            return this.removals.build();
        }

        @CheckReturnValue
        public SimpleParamsDiffSnapshot build() {
            List<Pair<Integer, Type>> sortedInsertions = this.insertions.stream().sorted(Comparator.comparingInt(Pair::getFirst)).toList();
            return new SimpleParamsDiffSnapshot(
                sortedInsertions,
                this.replacements.build(),
                this.swaps.build(),
                this.substitutes.build(),
                this.removals.build(),
                this.moves.build(),
                this.inlines.build()
            );
        }
    }
}
