package org.sinytra.adapter.patch.transformer.dynfix;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.patch.api.MethodContext;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchAuditTrail;

public interface DynamicFixer<DATA> {
    @Nullable
    DATA prepare(MethodContext methodContext);

    @Nullable
    FixResult apply(ClassNode classNode, MethodNode methodNode, MethodContext methodContext, PatchAuditTrail auditTrail, DATA data);

    record FixResult(Patch.Result result, PatchAuditTrail.Match match) {
        public FixResult {
            if (result == Patch.Result.PASS) {
                throw new IllegalArgumentException("Result must be non-PASS");
            }
        }

        @Nullable
        public static FixResult of(Patch.Result result, PatchAuditTrail.Match match) {
            return result == Patch.Result.PASS ? null : new FixResult(result, match);
        }
    }

    final class EmptyData {
        public static final EmptyData INSTANCE = new EmptyData();
    }
}
