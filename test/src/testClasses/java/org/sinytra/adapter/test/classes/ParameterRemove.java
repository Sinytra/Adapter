package org.sinytra.adapter.test.classes;

import java.util.concurrent.atomic.AtomicBoolean;

public class ParameterRemove {
    public static void testSimple(int p1, char p2) {

    }

    public static void testWrapOp(SObj sObj, AtomicBoolean p1) {

    }

    public record SObj() {
        public void call(AtomicBoolean value) {

        }
    }
}
