package org.sinytra.adapter.patch.test.mixin;

import com.mojang.logging.LogUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.next.PipelineLegacyMethodTransformer;
import org.sinytra.adapter.patch.api.Patch;
import org.sinytra.adapter.patch.api.PatchEnvironment;
import org.sinytra.adapter.patch.api.RefmapHolder;
import org.sinytra.adapter.patch.fixes.FieldTypeUsageTransformer;
import org.sinytra.adapter.patch.transformer.dynfix.DynamicInjectionPointPatch;
import org.sinytra.adapter.patch.util.provider.ClassLookup;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;

public class DynamicMixinPatchTest extends MinecraftMixinPatchTest {
    private static final List<Patch> DYNAMIC_PATCHES = List.of(
        Patch.builder()
            .transform(new DynamicInjectionPointPatch())
            .transform(new PipelineLegacyMethodTransformer())
            .transform(new FieldTypeUsageTransformer())
            .build()
    );
    private static final Logger LOGGER = LogUtils.getLogger();

    private static PatchEnvironment patchEnvironment;

    @BeforeAll
    static void initialize() {
        ClassLookup cleanLookup = createCleanLookup();
        ClassLookup dirtyLookup = createDirtyLookup();
        patchEnvironment = PatchEnvironment.create(
            new RefmapHolder() {
                @Override
                public String remap(String cls, String reference) {
                    return reference;
                }

                @Override
                public void copyEntries(String from, String to) {
                }
            },
            cleanLookup,
            dirtyLookup,
            new BytecodeFixerUpperTestFrontend(cleanLookup, dirtyLookup).unwrap(),
            FabricUtil.COMPATIBILITY_LATEST
        );
    }

    @AfterAll
    static void postTest() {
        LOGGER.info("Complete report:\n\n{}", patchEnvironment.auditTrail().getCompleteReport());
    }

    @Test
    void testChangedTargetMethodWrapOperation() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/CropBlockMixin",
            "isOnFarmland",
            assertTargetMethod()
        );
    }

    @Test
    void testChangeMethodParamsInPipeline() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ServerEntityMixin",
            "packetWrap",
            assertTargetMethod()
        );
    }

    @Test
    void testChangeInjectMethodParamsInPipeline() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ServerEntityMixin",
            "modifyCreationData",
            assertTargetMethod()
        );
    }

    @Test
    void testChangeInjectionTargetInPipeline() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ServerEntityMixin",
            "markAsInitial",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

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
    void testMovedInjectionPointToMethod5() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LevelMixin",
            "modifyLeastStatus",
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
        assertSameCode(
            "org/sinytra/adapter/test/mixin/GuiMixin",
            "moveHealthDown",
            assertTargetMethod(),
            assertInjectionPoint()
        );
        assertSameCode(
            "org/sinytra/adapter/test/mixin/GuiMixin",
            "afterMainHud",
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
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LivingEntityMixin",
            "onUnderwater",
            assertUnique(),
            assertHasGeneratedMethod("org/sinytra/adapter/test/mixin/adapter_generated_CommonHooks")
        );
    }

    @Test
    void testCompareModifiedMethod2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LivingEntityMixin",
            "testFrostWalker",
            assertUnique(),
            assertHasGeneratedMethod("org/sinytra/adapter/test/mixin/adapter_generated_CommonHooks")
        );
    }

    @Test
    void testCompareModifiedMethod3() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/AbstractMinecartMixin",
            "skipVelocityClamping",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testModifiedToInstanceOfCall() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/StemBlockMixin",
            "isOnFarmland",
            assertTargetMethod(),
            assertTargetsConstant()
        );
    }

    @Test
    void testModifiedWrapOperationTarget() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/PumpkinBlockMixin",
            "isShears",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testModifiedWrapOperationTarget2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/TripWireBlockMixin",
            "isShears",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testModifiedWrapOperationTarget3() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/PiglinAiMixin",
            "isWearingGold",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testModifiedFieldType() throws Exception {
        assertSameField(
            "org/sinytra/adapter/test/mixin/CrossbowAttackGoalMixin",
            "mob"
        );
    }

    @Test
    void testDynamicParameterTypeAdapter() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/CrossbowAttackGoalMixin",
            "redirectedGetHandPossiblyHolding",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testDynamicLocalCaptureupgrade() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/GuiMixin",
            "cozyBackground",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Override
    protected LoadResult load(String className, List<String> allowedMethods) throws Exception {
        final ClassNode patched = loadClass(className);
        patched.methods.removeIf(m -> !allowedMethods.contains(m.name));
        DYNAMIC_PATCHES.forEach(p -> p.apply(patched, patchEnvironment));
        return new LoadResult(patchEnvironment, patched, loadClass(className));
    }
}
