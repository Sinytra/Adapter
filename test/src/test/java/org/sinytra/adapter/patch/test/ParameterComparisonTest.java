package org.sinytra.adapter.patch.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.analysis.params.ParamsEditDistance;
import org.sinytra.adapter.analysis.params.TypeRelation;
import org.sinytra.adapter.patch.test_main.mixin.MinecraftMixinPatchTest;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterComparisonTest {
    @Test
    public void testCompareInsertedParameters() {
        LayeredParamsDiffSnapshot diff = diff(
            List.of(Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)),
            List.of(Type.getType(String.class), Type.DOUBLE_TYPE, Type.FLOAT_TYPE, Type.INT_TYPE, Type.getType(Object.class))
        );
        assertEquals(2, diff.insertions().size());
        assertTrue(diff.replacements().isEmpty());
    }

    @Test
    public void testCompareReplacedParameters() {
        LayeredParamsDiffSnapshot diff = diff(
            List.of(Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)),
            List.of(Type.getType(String.class), Type.INT_TYPE, Type.getType(List.class))
        );
        assertTrue(diff.insertions().isEmpty());
        assertEquals(1, diff.replacements().size());
    }

    @Test
    public void testCompareCombinedParameters() {
        LayeredParamsDiffSnapshot diff = diff(
            List.of(Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)),
            List.of(Type.getType(String.class), Type.FLOAT_TYPE, Type.INT_TYPE, Type.getType(List.class), Type.DOUBLE_TYPE)
        );
        assertEquals(2, diff.insertions().size());
        assertEquals(1, diff.replacements().size());
    }

    @Test
    public void testCompareAppendedParameters() {
        LayeredParamsDiffSnapshot diff = diff(
            List.of(Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)),
            List.of(Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class), Type.FLOAT_TYPE, Type.DOUBLE_TYPE)
        );
        assertEquals(2, diff.insertions().size());
        assertEquals(3, diff.insertions().getFirst().index());
        assertTrue(diff.replacements().isEmpty());
    }

    @Test
    public void testMethodParameterNameComparison() throws IOException {
        ClassNode cleanNode = MinecraftMixinPatchTest.loadClass("org/sinytra/adapter/test_cls/common/CleanDummyClass");
        ClassNode dirtyNode = MinecraftMixinPatchTest.loadClass("org/sinytra/adapter/test_cls/common/DirtyDummyClass");

        MethodNode cleanMethod = cleanNode.methods.stream().filter(m -> m.name.equals("namedInsertionTest")).findFirst().orElseThrow();
        MethodNode dirtyMethod = dirtyNode.methods.stream().filter(m -> m.name.equals("namedInsertionTest")).findFirst().orElseThrow();

        LayeredParamsDiffSnapshot diff = ParamsEditDistance.diffParameters(cleanMethod, dirtyMethod, TypeRelation.STATIC).snapshot();
        assertEquals(1, diff.insertions().size());
        assertEquals(7, diff.insertions().getFirst().index());
        assertEquals(Type.FLOAT_TYPE, diff.insertions().getFirst().type());
        assertTrue(diff.replacements().isEmpty());
    }

    @Test
    public void testCompareRemovedParameters() {
        LayeredParamsDiffSnapshot diff = diff(
            List.of(Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class), Type.FLOAT_TYPE),
            List.of(Type.getType(String.class), Type.getType(Object.class), Type.FLOAT_TYPE, Type.DOUBLE_TYPE)
        );
        assertEquals(1, diff.insertions().size());
        assertTrue(diff.replacements().isEmpty());
        assertTrue(diff.swaps().isEmpty());
        assertEquals(1, diff.removals().size());
    }

    private static LayeredParamsDiffSnapshot diff(List<Type> clean, List<Type> dirty) {
        return ParamsEditDistance.diffTypes(clean, dirty).snapshot();
    }
}
