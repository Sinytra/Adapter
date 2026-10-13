package org.sinytra.adapter.patch.test.mc_1_21_1.mixin;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.logging.LogUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.assertj.core.api.Assertions;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.sinytra.adapter.api.ClassLookup;
import org.sinytra.adapter.api.DynamicPatches;
import org.sinytra.adapter.api.MethodTransformer;
import org.sinytra.adapter.api.MixinClassGenerator;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.api.PatchResult;
import org.sinytra.adapter.api.Patcher;
import org.sinytra.adapter.api.RefmapHolder;
import org.sinytra.adapter.api.TxPhase;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.patch.test_main.mixin.MinecraftMixinPatchTest;
import org.sinytra.adapter.types.FieldTypeUsageTransformer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;
import java.util.stream.StreamSupport;

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
        patcher = Patcher.builder(patchEnvironment)
            .classTransformer(new FieldTypeUsageTransformer())
            .methodTransformers(ImmutableMultimap.<TxPhase, MethodTransformer>builder()
                .putAll(DynamicPatches.methodTransformers(List.of()))
                .put(TxPhase.VALIDATED, (context, config) -> {
                    if (context.methodNode().name.startsWith("failAfter")) {
                        throw new IllegalStateException("Simulated failure");
                    }
                    return PatchResult.PASS;
                })
                .build())
            .build();
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
    void testUpdatedInjectionPointModifyExprValConst() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/FarmLandBlockMixin",
            "changeIFrames",
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
    void testSplitMethodCancellation() throws Exception {
        assertPatched("org/sinytra/adapter/test/mixin/GuiMixin", "cancelAfterArmor", (patched, expected, env) -> {
            Assertions.assertThat(getTargetMethods(patched)).containsExactly("renderArmorLevel(Lnet/minecraft/client/gui/GuiGraphics;)V");

            ClassNode generated = env.classGenerator().getGeneratedMixinClasses().get("org/sinytra/adapter/test/mixin/adapter_generated_net_minecraft_client_gui_Gui").node();
            String tracker = "adapter$cancelled$GuiMixin$cancelAfterArmor";
            Assertions.assertThat(generated.fields).anyMatch(f -> f.name.equals(tracker) && f.desc.equals("Z"));

            AbstractInsnNode cancel = StreamSupport.stream(patched.instructions.spliterator(), false)
                .filter(insn -> insn instanceof MethodInsnNode call && call.name.equals("cancel"))
                .findFirst()
                .orElseThrow();
            Assertions.assertThat(cancel.getPrevious()).matches(insn -> insn instanceof FieldInsnNode set
                && set.getOpcode() == Opcodes.PUTFIELD && set.owner.equals("net/minecraft/client/gui/Gui") && set.name.equals(tracker));

            List<String> cancelled = generated.methods.stream()
                .filter(m -> m.name.startsWith("cancelAfterArmor$adapter$canceller$"))
                .flatMap(m -> getTargetMethods(m).stream())
                .toList();
            Assertions.assertThat(cancelled).containsExactly(
                "renderFoodLevel(Lnet/minecraft/client/gui/GuiGraphics;)V",
                "renderAirLevel(Lnet/minecraft/client/gui/GuiGraphics;)V"
            );
        });
    }

    @Test
    void testRollbackSplitMethodCancellation() throws Exception {
        assertPatched("org/sinytra/adapter/test/mixin/GuiMixin", "failAfterCancelling", (patched, expected, env) -> {
            Assertions.assertThat(getTargetMethods(patched)).containsExactly("renderPlayerHealth");
            MixinClassGenerator.GeneratedClass generated = env.classGenerator().getGeneratedMixinClasses().get("org/sinytra/adapter/test/mixin/adapter_generated_net_minecraft_client_gui_Gui");
            if (generated != null) {
                Assertions.assertThat(generated.node().fields).noneMatch(f -> f.name.contains("failAfterCancelling"));
                Assertions.assertThat(generated.node().methods).noneMatch(m -> m.name.startsWith("failAfterCancelling"));
            }
        });
    }

    private static List<String> getTargetMethods(MethodNode method) {
        return new AnnotationHandle(method.visibleAnnotations.getFirst()).<List<String>>getValue("method").orElseThrow().get();
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
            assertHasGeneratedMethod("org/sinytra/adapter/test/mixin/adapter_generated_net_neoforged_neoforge_common_CommonHooks")
        );
    }

    @Test
    void testCompareModifiedMethod2() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LivingEntityMixin",
            "testFrostWalker",
            assertUnique(),
            assertHasGeneratedMethod("org/sinytra/adapter/test/mixin/adapter_generated_net_neoforged_neoforge_common_CommonHooks")
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

    @Test
    void testModifiedVariableIndex() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/GuiGraphicsMixin",
            "modifyRenderX",
            assertTargetMethod(),
            assertInjectionPoint(),
            assertIndex()
        );
    }

    // Disabled in CI as the resulting ordinal is the same when decomp/recomp is disabled
    @Test
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
    void testModifiedReturnIndex() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/MaceItemMixin",
            "rebalanceEquipment",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testAmbigousOverloadedTarget() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/GuiMixin",
            "renderSelectedItemName",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testChangedTargetMethod() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/BoneMealMixin",
            "cancelBonemeal",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testChangedLambdaIndex() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/MinecraftServerMixin",
            "thenCompose",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testExtractByCodePath() throws Exception {
        assertSameCodeExtracted(
            "org/sinytra/adapter/test/mixin/pipeline/BeaconBlockEntityMixin",
            "replaceDyeColor",
            "org/sinytra/adapter/test/mixin/pipeline/adapter_generated_net_neoforged_neoforge_common_extensions_IBlockExtension",
            assertTargetMethod()
        );
    }

    @Test
    void testExtractCapturingLocals() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/BeaconBlockEntityMixin",
            "captureBeamBlock",
            assertAudit("Can't relocate")
        );
    }

    @Test
    void testMirrorStaticHandler() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/BlockMixin",
            "onSpawnAfterBreak",
            assertUnique(),
            assertHasGeneratedMethod("org/sinytra/adapter/test/mixin/pipeline/adapter_generated_net_neoforged_neoforge_common_CommonHooks")
        );
    }

    @Test
    void testRollbackMirror() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/LivingEntityMixin",
            "failAfterMirror",
            assertNotRelocated("org/sinytra/adapter/test/mixin/adapter_generated_net_neoforged_neoforge_common_CommonHooks")
        );
    }

    @Test
    void testRollbackMove() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/BeaconBlockEntityMixin",
            "failAfterMove",
            assertNotRelocated("org/sinytra/adapter/test/mixin/pipeline/adapter_generated_net_neoforged_neoforge_common_extensions_IBlockExtension")
        );
    }

    // The call's owner was narrowed to a subclass
    @Test
    void testNarrowedCallOwner() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/FlowerPotBlockMixin",
            "emptyPotState",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    // The call's owner was widened to a superclass
    @Test
    void testWidenedCallOwnerRedirect() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/RangedBowAttackGoalMixin",
            "redirectTarget",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testWidenedCallOwnerWrapOperation() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mixin/pipeline/RangedBowAttackGoalMixin",
            "wrapTarget",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Override
    protected LoadResult load(String className, List<String> allowedMethods) throws Exception {
        ClassNode patched = loadClass(className);
        patched.methods.removeIf(m -> !allowedMethods.contains(m.name));
        patcher.process(patched);
        return new LoadResult(patchEnvironment, patched, loadClass(className));
    }
}
