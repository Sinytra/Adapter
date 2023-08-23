package dev.su5ed.sinytra.adapter.patch.test;

import dev.su5ed.sinytra.adapter.patch.ParametersDiff;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;

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
        ClassReader cleanReader = new ClassReader("dev.su5ed.sinytra.adapter.patch.test.CleanDummyClass");
        ClassNode cleanNode = new ClassNode();
        cleanReader.accept(cleanNode, 0);

        ClassReader dirtyReader = new ClassReader("dev.su5ed.sinytra.adapter.patch.test.DirtyDummyClass");
        ClassNode dirtyNode = new ClassNode();
        dirtyReader.accept(dirtyNode, 0);

        MethodNode cleanMethod = cleanNode.methods.stream().filter(m -> m.name.equals("namedInsertionTest")).findFirst().orElseThrow();
        MethodNode dirtyMethod = dirtyNode.methods.stream().filter(m -> m.name.equals("namedInsertionTest")).findFirst().orElseThrow();

        ParametersDiff diff = ParametersDiff.compareMethodParameters(cleanMethod, dirtyMethod);
        int originalCount = Type.getArgumentTypes(cleanMethod.desc).length;
        System.out.println("Original originalCount: " + originalCount);
        System.out.println("Actual originalCount: " + diff.originalCount());
        assertEquals(originalCount, diff.originalCount());

        System.out.println("Insertions:");
        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertEquals(1, diff.insertions().size());
        assertEquals(7, diff.insertions().get(0).getFirst());
        assertEquals(Type.FLOAT_TYPE, diff.insertions().get(0).getSecond());

        System.out.println("Replacements:");
        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
        assertTrue(diff.replacements().isEmpty());
    }

    // TODO Handle this case
//    @Test
//    public void testCompareComplexParameters() {
//        Type[] original = new Type[]{Type.getType(String.class), Type.INT_TYPE, Type.getType(Object.class)};
//        Type[] modified = new Type[]{Type.getType(String.class), Type.FLOAT_TYPE, Type.getType(Set.class), Type.DOUBLE_TYPE, Type.getType(Object.class), Type.INT_TYPE};
//
//        ParametersDiff diff = ParametersDiff.compareTypeParameters(original, modified);
//        System.out.println("Original originalCount: " + original.length);
//        System.out.println("Actual originalCount: " + diff.originalCount());
//        assertEquals(original.length, diff.originalCount());
//
//        System.out.println("Insertions:");
//        diff.insertions().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
//        assertEquals(2, diff.insertions().size());
//
//        System.out.println("Replacements:");
//        diff.replacements().forEach(param -> System.out.println("AT " + param.getFirst() + " TYPE " + param.getSecond()));
//        assertEquals(1, diff.replacements().size());
//    }
}
