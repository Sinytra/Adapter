package org.sinytra.adapter.patch.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.sinytra.adapter.analysis.params.ParamsEditDistance;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.edit.param.InjectParameterTransform;
import org.sinytra.adapter.edit.param.RemoveParameterTransformer;
import org.sinytra.adapter.edit.param.ReplaceParametersTransformer;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParamsDiffRegressionTest {

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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(clean, dirty);
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(clean, dirty);
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(clean, dirty);
        assertTrue(diff.insertions().isEmpty());
        assertTrue(diff.replacements().isEmpty());
        assertEquals(1, diff.removals().size());
        assertEquals(0, diff.removals().getFirst().index());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareSwappedParameters() {
        List<Type> original = List.of(
            Type.BOOLEAN_TYPE,
            Type.getObjectType("net/minecraft/world/level/block/entity/BlockEntity"),
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("net/minecraft/world/phys/HitResult$Type")
        );
        List<Type> modified = List.of(
            Type.BOOLEAN_TYPE,
            Type.getObjectType("net/minecraft/world/level/block/entity/BlockEntity"),
            Type.getObjectType("net/minecraft/world/phys/HitResult$Type"),
            Type.getObjectType("net/minecraft/world/item/ItemStack")
        );

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertTrue(diff.insertions().isEmpty());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertEquals(1, diff.swaps().size());
        assertTrue(diff.moves().isEmpty());
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(2, diff.insertions().getFirst().index());
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
        List<InjectParameterTransform> expectedInsertions = List.of(
            new InjectParameterTransform(0, Type.getObjectType("net/minecraftforge/client/event/RenderTooltipEvent$Pre")),
            new InjectParameterTransform(3, Type.INT_TYPE),
            new InjectParameterTransform(4, Type.INT_TYPE),
            new InjectParameterTransform(5, Type.getObjectType("org/joml/Vector2ic")),
            new InjectParameterTransform(6, Type.INT_TYPE),
            new InjectParameterTransform(7, Type.INT_TYPE)
        );

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
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
        List<InjectParameterTransform> expectedInsertions = List.of(
            new InjectParameterTransform(2, Type.BOOLEAN_TYPE),
            new InjectParameterTransform(3, Type.BOOLEAN_TYPE),
            new InjectParameterTransform(4, Type.DOUBLE_TYPE),
            new InjectParameterTransform(5, Type.getObjectType("net/minecraft/world/level/block/state/BlockState")),
            new InjectParameterTransform(6, Type.BOOLEAN_TYPE),
            new InjectParameterTransform(7, Type.BOOLEAN_TYPE),
            new InjectParameterTransform(8, Type.BOOLEAN_TYPE),
            new InjectParameterTransform(11, Type.getType(Iterator.class)),
            new InjectParameterTransform(12, Type.getType(String.class))
        );

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertEquals(5, diff.insertions().size());
        assertEquals(1, diff.insertions().get(0).index());
        assertEquals(9, diff.insertions().get(1).index());
        assertEquals(12, diff.insertions().get(2).index());
        assertEquals(13, diff.insertions().get(3).index());
        assertEquals(14, diff.insertions().get(4).index());
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertTrue(diff.insertions().isEmpty());
        assertEquals(1, diff.replacements().size());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertTrue(diff.moves().isEmpty());
    }

    @Test
    void testCompareReplacedParameters3() {
        List<Type> original = List.of(
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("net/minecraft/item/Item"),
            Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation")
        );
        List<Type> modified = List.of(
            Type.getObjectType("net/minecraft/world/item/ItemStack"),
            Type.getObjectType("net/minecraft/world/entity/LivingEntity"),
            Type.getObjectType("com/llamalad7/mixinextras/injector/wrapoperation/Operation")
        );

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertEquals(3, diff.insertions().size());
        assertEquals(new InjectParameterTransform(1, Type.FLOAT_TYPE), diff.insertions().get(0));
        assertEquals(new InjectParameterTransform(4, Type.FLOAT_TYPE), diff.insertions().get(1));
        assertEquals(new InjectParameterTransform(5, Type.DOUBLE_TYPE), diff.insertions().get(2));
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(new InjectParameterTransform(3, Type.DOUBLE_TYPE), diff.insertions().getFirst());
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

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(new InjectParameterTransform(2, Type.getType(Deque.class)), diff.insertions().getFirst());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertEquals(1, diff.moves().size());
    }

    @Test
    void testCompareComplexChanges() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.getType(List.class)
        );
        List<Type> modified = List.of(
            Type.getType(Object.class)
        );

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertEquals(1, diff.replacements().size());
        assertEquals(new ReplaceParametersTransformer(0, Type.getType(Object.class)), diff.replacements().getFirst());
        assertEquals(1, diff.removals().size());
        assertEquals(1, diff.removals().getFirst().index());
    }

    @Test
    void testRemovedParamsOrder() {
        List<Type> original = List.of(
            Type.getType(String.class),
            Type.FLOAT_TYPE,
            Type.FLOAT_TYPE,
            Type.getType(Object.class),
            Type.getType(List.class),
            Type.INT_TYPE
        );
        List<Type> modified = List.of(
            Type.getType(String.class)
        );

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffTypes(original, modified);
        assertEquals(List.of(5, 4, 3, 2, 1), diff.removals().stream().map(RemoveParameterTransformer::index).toList());
    }
}
