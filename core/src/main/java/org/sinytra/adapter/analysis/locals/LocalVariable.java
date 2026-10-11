package org.sinytra.adapter.analysis.locals;

import org.objectweb.asm.Type;

public record LocalVariable(int index, Type type) {
}
