package org.sinytra.adapter.analysis.params;

import org.objectweb.asm.Type;

public interface TypeRelation {
    enum Kind {
        /// The same type
        SAME,
        /// A different type we can convert, such as a supertype or a number
        CONVERTIBLE,
        /// A type with nothing in common
        UNRELATED
    }

    Kind relate(Type clean, Type dirty);

    /// Class context-free comparison
    TypeRelation STATIC = (clean, dirty) -> {
        if (clean.equals(dirty)) {
            return Kind.SAME;
        }
        if (isReference(clean) && isReference(dirty) && (isObject(clean) || isObject(dirty))) {
            return Kind.CONVERTIBLE;
        }
        if (isNumber(clean) && isNumber(dirty)) {
            return Kind.CONVERTIBLE;
        }
        return Kind.UNRELATED;
    };

    private static boolean isReference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    private static boolean isObject(Type type) {
        return type.getSort() == Type.OBJECT && type.getInternalName().equals("java/lang/Object");
    }

    private static boolean isNumber(Type type) {
        return switch (type.getSort()) {
            case Type.BYTE, Type.SHORT, Type.CHAR, Type.INT, Type.LONG, Type.FLOAT, Type.DOUBLE -> true;
            default -> false;
        };
    }
}
