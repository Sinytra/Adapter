package org.sinytra.adapter.patch.test;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.sinytra.adapter.next.env.param.ParamDiffResolver;
import org.sinytra.adapter.next.env.param.ParamDiffResolver.ParamEvalResult;
import org.sinytra.adapter.next.env.param.ParamDiffResolver.ParamState;
import org.sinytra.adapter.patch.analysis.params.EnhancedParamsDiff;
import org.sinytra.adapter.patch.analysis.params.LayeredParamsDiffSnapshot;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ParamDiffResolverTest {

    @Test
    void testInjectParameter() {
        // Include duplicate InputStream.class to ensure it is reference-sensitive
        List<Type> clean = create(InputStream.class, String.class, Integer.class, InputStream.class, OutputStream.class);
        List<Type> dirty = create(InputStream.class, String.class, Integer.class, Double.class, InputStream.class, OutputStream.class);

        testParams(clean, dirty, 3, 4);
    }

    @Test
    void testReplaceParameterType() {
        List<Type> clean = create(String.class, Integer.class, InputStream.class, OutputStream.class);
        List<Type> dirty = create(String.class, Integer.class, Double.class, OutputStream.class);

        testParams(clean, dirty, 3, 3);
    }

    @Test
    void testSwapParameter() {
        List<Type> clean = create(String.class, Integer.class, InputStream.class, OutputStream.class);
        List<Type> dirty = create(String.class, Integer.class, OutputStream.class, InputStream.class);

        testParams(clean, dirty, 2, 3);
    }

    @Test
    void testMoveParameter() {
        List<Type> clean = create(String.class, Integer.class, InputStream.class, OutputStream.class);
        List<Type> dirty = create(Integer.class, InputStream.class, String.class, OutputStream.class);

        testParams(clean, dirty, 0, 2);
    }

    @Test
    void testRemoveParameter() {
        List<Type> clean = create(String.class, Integer.class, InputStream.class, OutputStream.class);
        List<Type> dirty = create(String.class, Integer.class, OutputStream.class);

        testParams(clean, dirty, 2, -1);
    }

    private static void testParams(List<Type> clean, List<Type> dirty, int oldPos, int newPos) {
        testParams(clean, dirty, oldPos, newPos, newPos == -1 ? null : dirty.get(newPos));
    }

    private static void testParams(List<Type> clean, List<Type> dirty, int oldPos, int newPos, Type newType) {
        Type original = clean.get(oldPos);

        LayeredParamsDiffSnapshot snapshot = EnhancedParamsDiff.createLayered(clean, dirty);
        ParamEvalResult result = ParamDiffResolver.resolve(clean, snapshot);

        ParamState updated = result.getUpdated(original);
        if (newPos == -1) {
            assertNull(updated);
        } else {
            assertNotNull(updated);
            assertEquals(newPos, updated.dirtyIndex());
            assertEquals(newType, updated.type());   
        }
    }

    private static List<Type> create(Class<?>... cls) {
        return Stream.of(cls)
            .map(Type::getType)
            .toList();
    }
}
