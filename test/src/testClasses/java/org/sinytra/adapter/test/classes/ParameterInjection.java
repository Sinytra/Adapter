package org.sinytra.adapter.test.classes;

import java.util.concurrent.atomic.AtomicInteger;

public class ParameterInjection {
    public void testRedirect(String a, int b) {
        a.repeat(b);
    }

    public void testTargetBig(long a, double b, float c) {

    }

    public void testTargetWrap(SomeClass someClass, String p1, AtomicInteger p2) {
        someClass.execute(p1, p2);
    }

    public record SomeClass() {
        public void execute(String p1, AtomicInteger p2) {

        }
    }
}
