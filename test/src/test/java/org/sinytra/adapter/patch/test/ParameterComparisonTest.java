package org.sinytra.adapter.patch.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.next.transform.preprocess.CapturedLocalsPreProcessor;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;
import org.sinytra.adapter.patch.analysis.params.ParametersDiff;
import org.sinytra.adapter.patch.test.mixin.MinecraftMixinPatchTest;

import java.io.IOException;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterComparisonTest {
    @Test
    public void testCompareInsertedParameters() {
        Type[] original = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)};
        Type[] modified = new Type[]{Type.getType(String.class), Type.DOUBLE_TYPE, Type.FLOAT_TYPE, Type.INT_TYPE, Type.getType(Object.class)};

        ParametersDiff diff = ParametersDiff.compareTypeParameters(original, modified);
        System.out.println("Original originalCount: " + original.length);
        System.out.println("Actual originalCount: " + diff.originalCount());
        assertEquals(original.length, diff.originalCount());

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(2, diff.insertions().size());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.replacements().isEmpty());
    }

    @Test
    public void testCompareReplacedParameters() {
        Type[] original = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)};
        Type[] modified = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(List.class)};

        ParametersDiff diff = ParametersDiff.compareTypeParameters(original, modified);
        System.out.println("Original originalCount: " + original.length);
        System.out.println("Actual originalCount: " + diff.originalCount());
        assertEquals(original.length, diff.originalCount());

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.insertions().isEmpty());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(1, diff.replacements().size());
    }

    @Test
    public void testCompareCombinedParameters() {
        Type[] original = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)};
        Type[] modified = new Type[]{Type.getType(String.class), Type.FLOAT_TYPE, Type.INT_TYPE, Type.getType(List.class), Type.DOUBLE_TYPE};

        ParametersDiff diff = ParametersDiff.compareTypeParameters(original, modified);
        System.out.println("Original originalCount: " + original.length);
        System.out.println("Actual originalCount: " + diff.originalCount());
        assertEquals(original.length, diff.originalCount());

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(2, diff.insertions().size());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(1, diff.replacements().size());
    }

    @Test
    public void testCompareAppendedParameters() {
        Type[] original = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)};
        Type[] modified = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class), Type.FLOAT_TYPE, Type.DOUBLE_TYPE};

        ParametersDiff diff = ParametersDiff.compareTypeParameters(original, modified);
        System.out.println("Original originalCount: " + original.length);
        System.out.println("Actual originalCount: " + diff.originalCount());
        assertEquals(original.length, diff.originalCount());

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(2, diff.insertions().size());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.replacements().isEmpty());
    }

    @Test
    public void testMethodParameterNameComparison() throws IOException {
        ClassNode cleanNode = MinecraftMixinPatchTest.loadClass("org/sinytra/adapter/test/CleanDummyClass");
        ClassNode dirtyNode = MinecraftMixinPatchTest.loadClass("org/sinytra/adapter/test/DirtyDummyClass");

        MethodNode cleanMethod = cleanNode.methods.stream().filter(m -> m.name.equals("namedInsertionTest")).findFirst().orElseThrow();
        MethodNode dirtyMethod = dirtyNode.methods.stream().filter(m -> m.name.equals("namedInsertionTest")).findFirst().orElseThrow();

        LayeredParamsDiffSnapshot diff = EnhancedParamsDiff.compareMethodParameters(cleanMethod, dirtyMethod);

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(1, diff.insertions().size());
        assertEquals(7, diff.insertions().getFirst().getFirst());
        assertEquals(Type.FLOAT_TYPE, diff.insertions().getFirst().getSecond());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.replacements().isEmpty());
    }

    @Test
    public void testCompareRemovedParameters() {
        Type[] original = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class), Type.FLOAT_TYPE};
        Type[] modified = new Type[]{Type.getType(String.class), Type.getType(Object.class), Type.FLOAT_TYPE, Type.DOUBLE_TYPE};

        ParametersDiff diff = ParametersDiff.compareTypeParameters(original, modified);
        System.out.println("Original originalCount: " + original.length);
        System.out.println("Actual originalCount: " + diff.originalCount());
        assertEquals(original.length, diff.originalCount());

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(1, diff.insertions().size());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.replacements().isEmpty());

        System.out.println("Swaps:");
        diff.swaps().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.swaps().isEmpty());

        System.out.println("Removals:");
        diff.removals().forEach(param -> System.out.println("AT " + param));
        assertEquals(1, diff.removals().size());
    }

    @Test
    public void testCompareReorderedParameters() {
        Type[] original = new Type[]{Type.getType(String.class), Type.getType(List.class), Type.BOOLEAN_TYPE, Type.BOOLEAN_TYPE, Type.getType(Set.class), Type.getType(Map.class)};
        Type[] modified = new Type[]{Type.getType(String.class), Type.getType(List.class), Type.getType(Deque.class), Type.getType(Map.class), Type.BOOLEAN_TYPE, Type.BOOLEAN_TYPE, Type.getType(Set.class)};

        LayeredParamsDiffSnapshot diff = CapturedLocalsPreProcessor.rearrangeParameters(List.of(original), List.of(modified));

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(1, diff.insertions().size());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.replacements().isEmpty());

        System.out.println("Swaps:");
        diff.swaps().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(3, diff.swaps().size());

        System.out.println("Removals:");
        diff.removals().forEach(param -> System.out.println("AT " + param));
        assertTrue(diff.removals().isEmpty());
    }
}
