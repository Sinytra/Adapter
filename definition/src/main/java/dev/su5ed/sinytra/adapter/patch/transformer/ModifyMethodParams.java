package dev.su5ed.sinytra.adapter.patch.transformer;

import dev.su5ed.sinytra.adapter.patch.LVTFixer;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ModifyMethodParams extends ModifyMethodParamsBase {
    private final Consumer<List<Type>> operator;

    public ModifyMethodParams(Consumer<List<Type>> operator, @Nullable LVTFixer lvtFixer) {
        super(lvtFixer);

        this.operator = operator;
    }

    @Override
    protected Type[] getReplacementParameters(Type[] original) {
        List<Type> list = new ArrayList<>(Arrays.asList(original));
        this.operator.accept(list);
        return list.toArray(Type[]::new);
    }
}
