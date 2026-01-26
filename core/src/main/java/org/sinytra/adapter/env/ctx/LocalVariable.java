package org.sinytra.adapter.env.ctx;

import org.objectweb.asm.Type;

public record LocalVariable(int index, Type type) {
}
