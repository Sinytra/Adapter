package org.sinytra.adapter.runtime;

import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.runtime.inject.InstanceOfInjectionPoint;
import org.sinytra.adapter.runtime.inject.ModifyInstanceofValueInjectionInfo;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;

import java.util.List;
import java.util.Set;

public class AdapterMixinPlugin implements IMixinConfigPlugin {

    static {
        InjectionPoint.register(InstanceOfInjectionPoint.class, null);
        InjectionInfo.register(ModifyInstanceofValueInjectionInfo.class);
    }

    //@formatter:off
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() {return "";}
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {return true;}
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() {return List.of();}
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    //@formatter:on
}
