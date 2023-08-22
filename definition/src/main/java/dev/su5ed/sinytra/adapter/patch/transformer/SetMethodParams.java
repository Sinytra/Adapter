package dev.su5ed.sinytra.adapter.patch.transformer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.su5ed.sinytra.adapter.patch.CodecUtil;
import dev.su5ed.sinytra.adapter.patch.MethodTransform;
import dev.su5ed.sinytra.adapter.patch.Patch;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SetMethodParams extends ModifyMethodParamsBase {
    public static final Codec<SetMethodParams> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        CodecUtil.TYPE_CODEC.listOf().fieldOf("replacementTypes").forGetter(m -> m.replacementTypes)
    ).apply(instance, SetMethodParams::new));
    private static final Type CI_PARAM = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfo");
    private static final Type CIR_PARAM = Type.getObjectType("org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable");

    protected final List<Type> replacementTypes;

    public SetMethodParams(List<Type> replacementTypes) {
        super(null);

        this.replacementTypes = replacementTypes;
    }

    @Override
    public Codec<? extends MethodTransform> codec() {
        return CODEC;
    }

    @Override
    protected Type[] getReplacementParameters(Type[] original, AnnotationNode annotation) {
        if (annotation.desc.equals(Patch.INJECT)) {
            for (int i = 0; i < original.length; i++) {
                if (CI_PARAM.equals(original[i]) || CIR_PARAM.equals(original[i])) {
                    List<Type> types = new ArrayList<>(this.replacementTypes);
                    types.addAll(Arrays.asList(Arrays.copyOfRange(original, i, original.length)));
                    return types.toArray(Type[]::new);
                }
            }
        }
        return this.replacementTypes.toArray(Type[]::new);
    }
}
