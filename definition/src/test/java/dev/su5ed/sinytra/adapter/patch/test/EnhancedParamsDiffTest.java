package dev.su5ed.sinytra.adapter.patch.test;

import com.mojang.datafixers.util.Pair;
import dev.su5ed.sinytra.adapter.patch.analysis.EnhancedParamsDiff;
import dev.su5ed.sinytra.adapter.patch.analysis.ParametersDiff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        ParametersDiff diff = EnhancedParamsDiff.create(clean, dirty);
        Assertions.assertEquals(1, diff.insertions().size());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
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

        ParametersDiff diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(2, diff.insertions().size());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
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

        ParametersDiff diff = EnhancedParamsDiff.create(original, modified);
        assertTrue(diff.insertions().isEmpty());
        assertEquals(1, diff.replacements().size());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
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

        ParametersDiff diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(2, diff.insertions().size());
        assertEquals(1, diff.replacements().size());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
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

        ParametersDiff diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(3, diff.insertions().size());
        assertEquals(Pair.of(5, Type.DOUBLE_TYPE), diff.insertions().get(0));
        assertEquals(Pair.of(1, Type.FLOAT_TYPE), diff.insertions().get(1));
        assertEquals(Pair.of(4, Type.FLOAT_TYPE), diff.insertions().get(2));
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.removals().isEmpty());
        assertTrue(diff.swaps().isEmpty());
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

        ParametersDiff diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(Pair.of(4, Type.DOUBLE_TYPE), diff.insertions().get(0));
        assertTrue(diff.replacements().isEmpty());
        assertEquals(1, diff.removals().size());
        assertTrue(diff.swaps().isEmpty());
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

        ParametersDiff diff = EnhancedParamsDiff.create(original, modified);
        assertEquals(1, diff.insertions().size());
        assertEquals(Pair.of(2, Type.getType(Deque.class)), diff.insertions().get(0));
        assertTrue(diff.replacements().isEmpty());
        assertEquals(1, diff.moves().size());
        assertTrue(diff.removals().isEmpty());
    }
}
