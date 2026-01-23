package org.sinytra.adapter.patch.api;

import org.objectweb.asm.Type;

public record LocalVariable(int index, Type type) {
}
