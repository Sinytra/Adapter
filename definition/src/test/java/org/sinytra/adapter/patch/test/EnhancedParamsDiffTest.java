package org.sinytra.adapter.patch.test;

import com.mojang.datafixers.util.Pair;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.params.SimpleParamsDiffSnapshot;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnhancedParamsDiffTest {

    @Test
    void testMovedParameter() {
        List<Type> clean = List.of(
            Type.getObjectType("net/minecraft/world/level/block/state/BlockState"),
            Type.getObjectType("net/minecraft/world/level/block/entity/BlockEntity"),
            Type.getObjectType("net/minecraft/world/level/block/Block"),
            Type.BOOLEAN_TYPE,
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.BOOLEAN_TYPE
        );
        List<Type> dirty = List.of(
            Type.getObjectType("net/minecraft/world/level/block/state/BlockState"),
            Type.INT_TYPE,
            Type.getObjectType("net/minecraft/world/level/block/entity/BlockEntity"),
            Type.getObjectType("net/minecraft/world/level/block/Block"),
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(clean, dirty);
        assertEquals(1, diff.insertions().size());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertEquals(1, diff.moves().size());
    }

    @Test
    void testReplacedParameters() {
        List<Type> clean = List.of(
            Type.getObjectType("net/minecraft/world/item/enchantment/EnchantmentCategory"),
            Type.getObjectType("net/minecraft/world/item/Item"),
            Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation"),
            Type.getObjectType("com/llamalad7/mixinextras/sugar/ref/LocalRef")
        );
        List<Type> dirty = List.of(
            Type.getObjectType("net/minecraft/world/item/enchantment/Enchantment"),
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation"),
            Type.getObjectType("com/llamalad7/mixinextras/sugar/ref/LocalRef")
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(clean, dirty);
        assertTrue(diff.insertions().isEmpty());
        assertEquals(2, diff.replacements().size());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
    }

    @Test
    void testRemovedParameter() {
        List<Type> clean = List.of(
            Type.getType(Iterable.class),
            Type.getType(Iterator.class),
            Type.getObjectType("Lnet/minecraft/world/item/ItemStack;"),
            Type.getType("Lnet/minecraft/world/item/Item;")
        );
        List<Type> dirty = List.of(
            Type.getType(Iterator.class),
            Type.getObjectType("Lnet/minecraft/world/item/ItemStack;"),
            Type.getType("Lnet/minecraft/world/item/Item;")
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(clean, dirty);
        assertTrue(diff.insertions().isEmpty());
        assertTrue(diff.replacements().isEmpty());
        assertEquals(1, diff.removals().size());
        assertEquals(0, diff.removals().get(0));
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareSwappedParameters() {
        List<Type> original = List.of(
            Type.getObjectType("net/minecraft/core/BlockPos"),
            Type.getObjectType("net/minecraft/world/level/block/state/BlockState"),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("net/minecraft/world/item/context/UseOnContext")
        );
        List<Type> modified = List.of(
            Type.getObjectType("net/minecraft/core/BlockPos"),
            Type.getObjectType("net/minecraft/world/level/block/state/BlockState"),
            Type.getObjectType("net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock"),
            Type.getObjectType("net/minecraft/world/item/context/UseOnContext"),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("net/minecraft/world/InteractionResult")
        );

        LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.createLayered(original, modified);
        //        assertEquals(2, diff.insertions().size());
        //        assertTrue(diff.replacements().isEmpty());
        //        assertTrue(diff.removals().isEmpty());
        //        assertTrue(diff.swaps().isEmpty());
        //        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareInsertedParameters() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(Object.class)
        );
        List<Type> modified = List.of(
            Type.getType(String.class),
            Type.DOUBLE_TYPE,
            Type.FLOAT_TYPE,
            Type.INT_TYPE,
            Type.getType(Object.class)
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(2, diff.insertions().size());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareInsertedParameters2() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(Object.class),
            Type.getType(Object.class)
        );
        List<Type> modified = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.DOUBLE_TYPE,
            Type.getType(Object.class),
            Type.getType(Object.class)
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(2, diff.insertions().get(0).getFirst());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareInsertedParameters3() {
        List<Type> original = List.of(
            Type.INT_TYPE,
            Type.INT_TYPE
        );
        List<Type> modified = List.of(
            Type.getObjectType("net/minecraftforge/client/event/RenderTooltipEvent$Pre"),
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.getObjectType("org/joml/Vector2ic"),
            Type.INT_TYPE,
            Type.INT_TYPE
        );
        List<Pair<Integer, Type>> expectedInsertions = List.of(
            Pair.of(0, Type.getObjectType("net/minecraftforge/client/event/RenderTooltipEvent$Pre")),
            Pair.of(3, Type.INT_TYPE),
            Pair.of(4, Type.INT_TYPE),
            Pair.of(5, Type.getObjectType("org/joml/Vector2ic")),
            Pair.of(6, Type.INT_TYPE),
            Pair.of(7, Type.INT_TYPE)
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(6, diff.insertions().size());
        assertEquals(expectedInsertions, diff.insertions());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareInsertedParametersOrder() {
        List<Type> original = List.of(
            Type.getObjectType("net/minecraft/world/level/block/Block"),
            Type.getObjectType("net/minecraft/core/BlockPos"),
            Type.getObjectType("net/minecraft/world/level/block/entity/BlockEntity"),
            Type.getObjectType("net/minecraft/nbt/CompoundTag")
        );
        List<Type> modified = List.of(
            Type.getObjectType("net/minecraft/world/level/block/Block"),
            Type.getObjectType("net/minecraft/core/BlockPos"),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.DOUBLE_TYPE,
            Type.getObjectType("net/minecraft/world/level/block/state/BlockState"),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.getObjectType("net/minecraft/world/level/block/entity/BlockEntity"),
            Type.getObjectType("net/minecraft/nbt/CompoundTag"),
            Type.getType(Iterator.class),
            Type.getType(String.class)
        );
        List<Pair<Integer, Type>> expectedInsertions = List.of(
            Pair.of(2, Type.BOOLEAN_TYPE),
            Pair.of(3, Type.BOOLEAN_TYPE),
            Pair.of(4, Type.DOUBLE_TYPE),
            Pair.of(5, Type.getObjectType("net/minecraft/world/level/block/state/BlockState")),
            Pair.of(6, Type.BOOLEAN_TYPE),
            Pair.of(7, Type.BOOLEAN_TYPE),
            Pair.of(8, Type.BOOLEAN_TYPE),
            Pair.of(11, Type.getType(Iterator.class)),
            Pair.of(12, Type.getType(String.class))
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(9, diff.insertions().size());
        assertEquals(expectedInsertions, diff.insertions());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareInsertedParametersIndexOffset() {
        List<Type> original = List.of(
            Type.getType(Collection.class),
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.getType("Lnet/minecraft/client/resources/MobEffectTextureManager;"),
            Type.getType(List.class),
            Type.getType(Iterator.class),
            Type.getType("Lnet/minecraft/world/effect/MobEffectInstance;"),
            Type.getType("Lnet/minecraft/world/effect/MobEffect;"),
            Type.INT_TYPE,
            Type.INT_TYPE
        );
        List<Type> modified = List.of(
            Type.getType(Collection.class),
            Type.getObjectType("Lnet/minecraft/client/gui/screens/Screen;"),
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.getType("Lnet/minecraft/client/resources/MobEffectTextureManager;"),
            Type.getType(List.class),
            Type.getType(Iterator.class),
            Type.getType("Lnet/minecraft/world/effect/MobEffectInstance;"),
            Type.getType("Lnet/minecraft/world/effect/MobEffect;"),
            Type.getType("Lnet/minecraftforge/client/extensions/common/IClientMobEffectExtensions;"),
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.FLOAT_TYPE,
            Type.INT_TYPE,
            Type.INT_TYPE
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(5, diff.insertions().size());
        assertEquals(1, diff.insertions().get(0).getFirst());
        assertEquals(9, diff.insertions().get(1).getFirst());
        assertEquals(12, diff.insertions().get(2).getFirst());
        assertEquals(13, diff.insertions().get(3).getFirst());
        assertEquals(14, diff.insertions().get(4).getFirst());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareReplacedParameters() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(Object.class)
        );
        List<Type> modified = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(List.class)
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertTrue(diff.insertions().isEmpty());
        assertEquals(1, diff.replacements().size());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareReplacedParameters2() {
        List<Type> original = List.of(
            Type.INT_TYPE,
            Type.INT_TYPE,
            Type.getType(List.class)
        );
        List<Type> modified = List.of(
            Type.FLOAT_TYPE,
            Type.INT_TYPE,
            Type.getType(List.class)
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertTrue(diff.insertions().isEmpty());
        assertEquals(1, diff.replacements().size());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareCombinedParameters() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(Object.class)
        );
        List<Type> modified = List.of(
            Type.getType(String.class),
            Type.FLOAT_TYPE,
            Type.INT_TYPE,
            Type.getType(List.class),
            Type.DOUBLE_TYPE
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(2, diff.insertions().size());
        assertEquals(1, diff.replacements().size());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareInsertedParametersAtDifferentPlaces() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(Object.class)
        );
        List<Type> modified = List.of(
            Type.getType(String.class),
            Type.FLOAT_TYPE,
            Type.INT_TYPE,
            Type.getType(Object.class),
            Type.FLOAT_TYPE,
            Type.DOUBLE_TYPE
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(3, diff.insertions().size());
        assertEquals(Pair.of(1, Type.FLOAT_TYPE), diff.insertions().get(0));
        assertEquals(Pair.of(4, Type.FLOAT_TYPE), diff.insertions().get(1));
        assertEquals(Pair.of(5, Type.DOUBLE_TYPE), diff.insertions().get(2));
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareRemovedParameters() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.INT_TYPE,
            Type.getType(Object.class),
            Type.FLOAT_TYPE
        );
        List<Type> modified = List.of(
            Type.getType(String.class),
            Type.getType(Object.class),
            Type.FLOAT_TYPE,
            Type.DOUBLE_TYPE
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(Pair.of(4, Type.DOUBLE_TYPE), diff.insertions().get(0));
        assertTrue(diff.replacements().isEmpty());
        assertEquals(1, diff.removals().size());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareReorderedParameters() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.getType(List.class),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.getType(Set.class),
            Type.getType(Map.class)
        );
        List<Type> modified = List.of(
            Type.getType(String.class),
            Type.getType(List.class),
            Type.getType(Deque.class),
            Type.getType(Map.class),
            Type.BOOLEAN_TYPE,
            Type.BOOLEAN_TYPE,
            Type.getType(Set.class)
        );

        SimpleParamsDiffSnapshot diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(Pair.of(2, Type.getType(Deque.class)), diff.insertions().get(0));
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertEquals(1, diff.moves().size());
    }
}
