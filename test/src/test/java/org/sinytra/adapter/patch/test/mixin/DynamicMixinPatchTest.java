package org.sinytra.adapter.patch.test.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.api.RefmapHolder;
import org.sinytra.adapter.patch.transformer.dynfix.DynamicInjectionPointPatch;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;

public class DynamicMixinPatchTest extends MinecraftMixinPatchTest {
    private static final List<Patch> DYNAMIC_PATCHES = List.of(
        Patch.builder()
//            .transform(new DynamicInjectorOrdinalPatch())
//            .transform(new DynamicLVTPatch(() -> lvtOffsets))
//            .transform(new DynamicAnonymousShadowFieldTypePatch())
//            .transform(new DynamicModifyVarAtReturnPatch())
//            .transform(new DynamicInheritedInjectionPointPatch())
//            .transform(new DynamicSyntheticInstanceofPatch())
            .transform(new DynamicInjectionPointPatch())
            .build()
//        Patch.interfaceBuilder()
//            .transform(new FieldTypePatchTransformer())
//            .build()
    );

    @Test
    void testUpdatedInjectionPoint() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/EffectRenderingInventoryScreenMixin",
            "onCollect",
            assertInjectionPoint()
        );
    }

    @Test
    void testAddedSameNameMethod() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/HumanoidArmorLayerMixin",
            "getArmorEntityGlint",
            assertTargetMethod()
        );
    }

    @Test
    void testUpdatedInjectionTargetSamePoint() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/BoatRendererMixin",
            "getBoatTextureAndModel",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testUpdatedInjectionPointFieldToMethod() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/HoeItemMixin",
            "injectUseOn",
            assertInjectionPoint()
        );
    }

    @Test
    void testUpdatedInjectionPoint2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/MilkBucketItemMixin",
            "onClearStatusEffect",
            assertInjectionPoint()
        );
    }

    @Test
    void testUpdatedInjectionPointModifyExprVal() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/FarmLandBlockMixin",
            "isFarmlandNearWater",
            assertInjectionPoint()
        );
    }

    @Test
    void testSplitMethodInjectionTarget() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/GuiMixin",
            "modifyTextureStatusBar",
            assertTargetMethod(),
            assertInjectionPoint()
        );
        assertSameCode(
            "org/sinytra/adapter/test/mixin/GuiMixin",
            "modifyTextureStatusBarsArmor",
            assertTargetMethod(),
            assertInjectionPoint()
        );
        assertSameCode(
            "org/sinytra/adapter/test/mixin/GuiMixin",
            "modifyTextureStatusBarsFood",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Override
    protected LoadResult load(String className) throws Exception {
        final ClassNode patched = loadClass(className);
        final PatchEnvironment env = PatchEnvironment.create(
            new RefmapHolder() {
                @Override
                public String remap(String cls, String reference) {
                    return reference;
                }

                @Override
                public void copyEntries(String from, String to) {
                }
            },
            createCleanLookup(),
            createDirtyLookup(),
            null,
            FabricUtil.COMPATIBILITY_LATEST
        );
        DYNAMIC_PATCHES.forEach(p -> p.apply(patched, env));
        return new LoadResult(patched, loadClass(className));
    }
}
