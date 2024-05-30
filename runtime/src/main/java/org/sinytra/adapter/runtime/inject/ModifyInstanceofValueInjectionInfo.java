package org.sinytra.adapter.runtime.inject;

import com.llamalad7.mixinextras.injector.MixinExtrasInjectionInfo;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.code.Injector;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.transformer.MixinTargetContext;

@InjectionInfo.AnnotationType(ModifyInstanceofValue.class)
@InjectionInfo.HandlerPrefix("modifyInstanceofValue")
public class ModifyInstanceofValueInjectionInfo extends MixinExtrasInjectionInfo {

    public ModifyInstanceofValueInjectionInfo(MixinTargetContext mixin, MethodNode method, AnnotationNode annotation) {
        super(mixin, method, annotation);
    }

    @Override
    protected Injector parseInjector(AnnotationNode injectAnnotation) {
        return new ModifyInstanceofValueInjector(this);
    }
}
