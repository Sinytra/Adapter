package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.CodecUtil;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import org.objectweb.asm.Type;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class SetTargetParams extends SetMethodParams {
    public static final Codec<SetTargetParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        CodecUtil.TYPE_CODEC.listOf().fieldOf("replacementTypes").forGetter(m -> m.replacementTypes)
    ).apply(instance, SetTargetParams::new));

    public SetTargetParams(List<Type> replacementTypes) {
        super(replacementTypes);
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    public Collection<String> getAcceptedAnnotations() {
        return Set.of(Patch.REDIRECT);
    }
}
