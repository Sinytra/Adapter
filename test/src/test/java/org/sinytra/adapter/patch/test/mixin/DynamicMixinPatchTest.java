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
            .transform(new DynamicInjectionPointPatch())
            .build()
    );

    @Test
    void testUpdatedInjectionPointAtAssignment() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/EffectRenderingInventoryScreenMixin",
            "onCollect",
            assertInjectionPoint()
        );
    }

    @Test
    void testResolveAmbigousMethodName() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/HumanoidArmorLayerMixin",
            "getArmorEntityGlint",
            assertTargetMethod()
        );
    }

    @Test
    void testMovedInjectionPointToMethod() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/BoatRendererMixin",
            "getBoatTextureAndModel",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testMovedInjectionPointToMethod2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/EntityMixin",
            "bypassMovementInFluidCalls",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testMovedInjectionPointToMethod3() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/EntityMixin",
            "preventPushFromFluids",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testMovedInjectionPointToMethod4() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/AbstractMinecartMixin",
            "modifiedMovement",
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
    void testUpdatedInjectionPointFieldToMethod2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/NaturalSpawnerMixin",
            "getSpawnEntriesMixin",
            assertInjectionPoint()
        );
    }

    @Test
    void testUpdatedArbitraryInjectionPoint() throws Exception {
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

    @Test
    void testModifiedSliceTarget() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LivingEntityMixin",
            "getSlipperinessForIceSkates",
            assertTargetMethod(),
            assertInjectionPoint(),
            assertSliceRange()
        );
    }

    @Test
    void testCompareModifiedMethod() throws Exception {
        // TODO This can correctly determine the injection point in the extracted method now,
        // but fails to extract because the mixin calls an injected unique method.
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LivingEntityMixin",
            "onUnderwater",
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
