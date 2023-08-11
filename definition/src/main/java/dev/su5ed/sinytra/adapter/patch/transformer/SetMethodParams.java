package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.PatchSerialization;
import org.objectweb.asm.Type;

import java.util.List;

public class SetMethodParams extends ModifyMethodParamsBase {
    public static final Codec<SetMethodParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        PatchSerialization.TYPE_CODEC.listOf().fieldOf("replacementTypes").forGetter(m -> m.replacementTypes)
    ).apply(instance, SetMethodParams::new));

    private final List<Type> replacementTypes;

    public SetMethodParams(List<Type> replacementTypes) {
        super(null);

        this.replacementTypes = replacementTypes;
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    protected boolean areParamsComplete() {
        return false;
    }

    @Override
    protected Type[] getReplacementParameters(Type[] original) {
        return this.replacementTypes.toArray(Type[]::new);
    }
}
