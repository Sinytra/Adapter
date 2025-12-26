package org.sinytra.adapter.next.env.ann;

import org.jetbrains.annotations.Nullable;
import org.sinytra.adapter.patch.util.MethodQualifier;

import java.util.OptionalInt;

public class ModifyVariableMixinData extends MixinData {
    private final boolean argsOnly;
    private final Integer ordinal;
    @Nullable
    private final SliceData slice;
    
    public ModifyVariableMixinData(ClassTarget targetClass, MethodQualifier targetMethod, AtData atData, boolean argsOnly, Integer ordinal, @Nullable SliceData slice) {
        super(targetClass, targetMethod, atData);

        this.argsOnly = argsOnly;
        this.ordinal = ordinal;
        this.slice = slice;
    }

    public boolean argsOnly() {
        return this.argsOnly;
    }

    public OptionalInt ordinal() {
        return this.ordinal != null ? OptionalInt.of(this.ordinal) : OptionalInt.empty();
    }

    @Nullable
    public SliceData slice() {
        return this.slice;
    }
}
