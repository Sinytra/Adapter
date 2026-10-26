package org.sinytra.adapter.analysis.params;

import org.objectweb.asm.Type;
import org.sinytra.adapter.analysis.method.InheritanceHandler;

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

    static TypeRelation inheritance(InheritanceHandler inheritance) {
        return (clean, dirty) -> {
            Kind kind = STATIC.relate(clean, dirty);
            if (kind == Kind.UNRELATED && clean.getSort() == Type.OBJECT && dirty.getSort() == Type.OBJECT
                && (inheritance.isClassInherited(clean.getInternalName(), dirty.getInternalName())
                || inheritance.isClassInherited(dirty.getInternalName(), clean.getInternalName()))
            ) {
                return Kind.CONVERTIBLE;
            }
            return kind;
        };
    }

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
