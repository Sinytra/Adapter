package org.sinytra.adapter.patch.test.mixin;

import com.mojang.logging.LogUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.patch.DynamicPatches;
import org.sinytra.adapter.patch.Patcher;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.RefmapHolder;
import org.sinytra.adapter.types.FieldTypeUsageTransformer;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;

public class DynamicMixinPatchTest extends MinecraftMixinPatchTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Patcher patcher;
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
        patcher = new Patcher(
            patchEnvironment,
            List.of(new FieldTypeUsageTransformer()),
            DynamicPatches.methodTransformers(List.of())
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
    void testChangedTargetMethodWrapOperation2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/PlayerMixin",
            "testGetDestroySpeed",
            assertTargetMethod()
        );
    }

    @Test
    void testChangedTargetMethodModifyExpressionValue() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/CropBlockMixin",
            "getAvailableMoisture",
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
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testChangeInjectMethodParamsInPipeline2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ServerboundCustomPayloadPacketMixin",
            "modifyCodec",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testTargetMethodAndInjectionPointChangedInPipeline() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ClientLanguageMixin",
            "saveSeparately",
            assertTargetMethod(),
            assertInjectionPoint()
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
    void testChangeInjectionTargetInPipelineMEV() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ElytraLayerMixin",
            "canRenderElytra",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testChangeTargetToLambdaInPipeline() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/TitleScreenMixin",
            "onRender",
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
            "moveAirUp",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testMethodInjectionTargetParamsChanged() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LevelRendererMixin",
            "postRenderParticles",
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

    @Test
    void testSyntheticInstanceof() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ItemInHandRendererMixin",
            "renderFirstPersonItem"
        );
    }

    @Test
    void testSyntheticInstanceofMEV() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/ItemInHandRendererMixin",
            "renderFirstPersonItemMEV",
            assertType(),
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Override
    protected LoadResult load(String className, List<String> allowedMethods) throws Exception {
        ClassNode patched = loadClass(className);
        patched.methods.removeIf(m -> !allowedMethods.contains(m.name));
        patcher.apply(patched);
        return new LoadResult(patchEnvironment, patched, loadClass(className));
    }
}
