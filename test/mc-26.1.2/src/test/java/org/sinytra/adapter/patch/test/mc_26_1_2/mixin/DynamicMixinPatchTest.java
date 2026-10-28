package org.sinytra.adapter.patch.test.mc_26_1_2.mixin;

import com.mojang.logging.LogUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.sinytra.adapter.api.ClassLookup;
import org.sinytra.adapter.api.DynamicPatches;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.api.Patcher;
import org.sinytra.adapter.api.RefmapHolder;
import org.sinytra.adapter.model.ann.AnnotationHandle;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.patch.test_main.mixin.MinecraftMixinPatchTest;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
            .classTransformers(DynamicPatches.CLASS_PATCHES)
            .methodTransformers(DynamicPatches.methodTransformers(List.of()))
            .build();
    }

    @AfterAll
    static void postTest() {
        LOGGER.info("Complete report:\n\n{}", patchEnvironment.auditTrail().getCompleteReport());
    }

    @Test
    void testModifyConstantMovedIntoExtension() throws Exception {
        assertSameCodeExtracted(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/LivingEntityMixin",
            "sinkSpeed",
            "org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_net_neoforged_neoforge_common_extensions_ILivingEntityExtension",
            assertTargetMethod()
        );
    }

    @Test
    void testWrapOperationMovedIntoExtension() throws Exception {
        assertSameCodeExtracted(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/PowderSnowBlockMixin",
            "walkOnSnow",
            "org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_net_neoforged_neoforge_common_extensions_IItemExtension",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testAnonymousTargetUnchanged() throws Exception {
        assertMixinTarget("org/sinytra/adapter/test/mc_26_1_2/mixin/DispenseItemBehaviorMixin", "dispense", "net/minecraft/core/dispenser/DispenseItemBehavior$10");
    }

    @Test
    void testAnonymousTargetRenumbered() throws Exception {
        assertMixinTarget("org/sinytra/adapter/test/mc_26_1_2/mixin/FunctionCommandExecutorMixin", "readArguments", "net/minecraft/server/commands/FunctionCommand$2");
    }

    @Test
    void testFieldTypeAdaptedOnce() throws Exception {
        ClassNode patched = load("org/sinytra/adapter/test/mc_26_1_2/mixin/RangedCrossbowAttackGoalMixin", List.of("readMob")).patched();
        String once = describeMethod(patched, "readMob");
        patcher.process(patched);
        assertEquals(once, describeMethod(patched, "readMob"));
        assertEquals(1, once.split("CHECKCAST net/minecraft/world/entity/monster/Monster", -1).length - 1, once);
    }

    @Test
    void testFieldTypeChangedAccessor() throws Exception {
        ClassNode patched = load("org/sinytra/adapter/test/mc_26_1_2/mixin/RangedCrossbowAttackGoalAccessor", List.of("getMob")).patched();
        MethodNode accessor = patched.methods.stream().filter(m -> m.name.equals("getMob")).findFirst().orElseThrow();
        assertNull(AdapterUtil.getAnnotation(accessor.visibleAnnotations, MixinAnnotations.ACCESSOR));
        AnnotationNode invoker = AdapterUtil.getAnnotation(accessor.visibleAnnotations, MixinAnnotations.INVOKER);
        assertNotNull(invoker);
        assertEquals("adapter$mob()Lnet/minecraft/world/entity/monster/Monster;", AdapterUtil.getAnnotationValue(invoker, "value"));
    }

    private static String describeMethod(ClassNode classNode, String name) {
        MethodNode method = classNode.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
        Textifier textifier = new Textifier();
        method.accept(new TraceMethodVisitor(textifier));
        return String.join("", textifier.getText().stream().map(String::valueOf).toList());
    }

    @Test
    void testExtractUsingCodePath() throws Exception {
        assertSameCodeExtracted(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/LivingEntityMixin",
            "playSoundCorrectlyForBlocks",
            "org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_net_neoforged_neoforge_common_extensions_IBlockExtension",
            assertTargetMethod()
        );
    }

    @Test
    void testModifyArgChangedTargetDesc() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/LevelRendererMixin",
            "clampMaxValue",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testShiftedVariableToExpression() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/MinecraftMixin",
            "modifyAttackedPos",
            assertType(),
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testTargetInheritedDelegatingDefault() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/MultiPartModelMixin",
            "onSetSeed",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testModifyVariableCapturedTargetArgs() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/MultiPartModelMixin",
            "modifyRandom",
            assertTargetMethod()
        );
    }

    @Test
    void testAppliedOrdinalShift() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/CameraMixin",
            "addRoll1",
            assertTargetMethod(),
            assertInjectionPoint(),
            assertAudit("ordinal 1 now selects another instruction")
        );
    }

    @Test
    void testShiftedOrdinal() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/CameraMixin",
            "addRoll3",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testWrapWithConditionReplacedCall() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/CameraMixin",
            "addRoll2",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testMovedAndChangedCall() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/LevelRendererMixin",
            "beforeExtractRenderState",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testSplitPieceOfRemovedTarget() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/GuiMixin",
            "skipContextualBar",
            assertTargetMethod(),
            assertInjectionPoint()
        );
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/GuiMixin",
            "skipContextualBarBackground",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testMirrorIntoInstanceCallee() throws Exception {
        assertMirroredStepSound("adjustStepSoundVolume");
    }

    @Test
    void testMirrorFromChangedTarget() throws Exception {
        assertMirroredStepSound("adjustMuffledStepSoundVolume");
    }

    @Test
    void testHandlerUsingThisNotMoved() throws Exception {
        assertMirroredStepSound("adjustStepSoundVolumeOfThis");
    }

    @Test
    void testWrapOperationMirrorGuarded() throws Exception {
        String generatedName = "org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_net_neoforged_neoforge_common_extensions_IBlockExtension";
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/EntityMixin",
            "wrapStepSound",
            assertUnique(),
            assertHasGeneratedMethod(generatedName),
            (patched, expected, env) -> {
                ClassNode generated = env.classGenerator().getGeneratedMixinClasses().get(generatedName).node();
                MethodNode mirror = generated.methods.stream().filter(m -> m.name.startsWith("wrapStepSound$adapter$mirror")).findFirst().orElseThrow();
                assertVerifies(generated, mirror);
                Assertions.assertThat(AdapterUtil.methodNodeToString(mirror))
                    .as("Mirror calls the original operation when it doesn't run the handler")
                    .contains("INVOKEVIRTUAL net/minecraft/world/entity/Entity.adapter$via$EntityMixin$wrapStepSound ()Z")
                    .contains("INVOKEINTERFACE com/llamalad7/mixinextras/injector/wrapoperation/Operation.call ([Ljava/lang/Object;)Ljava/lang/Object;");
            }
        );
    }

    private static void assertVerifies(ClassNode owner, MethodNode method) {
        try {
            new Analyzer<>(new BasicVerifier()).analyze(owner.name, method);
        } catch (AnalyzerException e) {
            throw new AssertionError("Invalid bytecode in " + method.name + "\n" + AdapterUtil.methodNodeToString(method), e);
        }
    }

    @Test
    void testMirrorGuardedByOriginCall() throws Exception {
        assertMirroredStepSound("adjustCombinationStepSoundVolume");
        ClassNode guards = patchEnvironment.classGenerator().getGeneratedMixinClasses()
            .get("org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_net_minecraft_world_entity_Entity").node();
        MethodNode enter = guards.methods.stream().filter(m -> m.name.equals("adapter$via$EntityMixin$adjustCombinationStepSoundVolume$enter")).findFirst().orElseThrow();
        AnnotationHandle inject = new AnnotationHandle(enter.visibleAnnotations.getFirst());
        Assertions.assertThat(inject.<List<String>>getValue("method").orElseThrow().get())
            .containsExactly("playCombinationStepSounds(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)V");
        Assertions.assertThat(AdapterUtil.methodNodeToString(enter)).contains("target=\"Lnet/minecraft/world/level/block/state/BlockState;playStepSound(");
    }

    private void assertMirroredStepSound(String handler) throws Exception {
        String generatedName = "org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_net_neoforged_neoforge_common_extensions_IBlockExtension";
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/EntityMixin",
            handler,
            assertUnique(),
            assertHasGeneratedMethod(generatedName),
            (patched, expected, env) -> {
                ClassNode generated = env.classGenerator().getGeneratedMixinClasses().get(generatedName).node();
                MethodNode mirror = generated.methods.stream().filter(m -> m.name.startsWith(handler + "$adapter$mirror")).findFirst().orElseThrow();
                assertVerifies(generated, mirror);
                Assertions.assertThat(mirror.desc).isEqualTo("(FLnet/minecraft/world/entity/Entity;)F");
                AnnotationHandle modifyArg = new AnnotationHandle(mirror.visibleAnnotations.getFirst());
                Assertions.assertThat(modifyArg.<List<String>>getValue("method").orElseThrow().get())
                    .containsExactly("playStepSound(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;FF)V");
                AnnotationNode local = mirror.invisibleParameterAnnotations[1].getFirst();
                Assertions.assertThat(local.values).containsExactly("argsOnly", true, "index", 4);
                MethodInsnNode active = (MethodInsnNode) StreamSupport.stream(mirror.instructions.spliterator(), false)
                    .filter(MethodInsnNode.class::isInstance)
                    .findFirst()
                    .orElseThrow();
                Assertions.assertThat(active.owner + "." + active.name + active.desc)
                    .as("Mirror runs only for calls from the original method")
                    .isEqualTo("net/minecraft/world/entity/Entity.adapter$via$EntityMixin$" + handler + "()Z");
            }
        );
    }

    @Test
    void testModifyExpressionValueReplacedHook() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/LivingEntityItemMixin",
            "cancelItemUsing",
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Test
    void testModifyArgsMovedIntoHook() throws Exception {
        LoadResult result = load("org/sinytra/adapter/test/mc_26_1_2/mixin/FogRendererMixin", List.of("darkenFogColor"));
        ClassNode generated = result.env().classGenerator().getGeneratedMixinClasses()
            .get("org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_net_neoforged_neoforge_client_ClientHooks").node();
        MethodNode moved = generated.methods.stream().filter(m -> m.name.equals("darkenFogColor")).findFirst().orElseThrow();
        Assertions.assertThat(moved.access & Opcodes.ACC_STATIC).as("Static handler").isNotZero();
        Assertions.assertThat(moved.access & Opcodes.ACC_PRIVATE).as("Private static handler").isNotZero();
        AnnotationHandle modifyArgs = new AnnotationHandle(moved.visibleAnnotations.getFirst());
        Assertions.assertThat(modifyArgs.<List<String>>getValue("method").orElseThrow().get())
            .containsExactly("getFogColor(Lnet/minecraft/client/Camera;FLnet/minecraft/client/multiplayer/ClientLevel;IFFFFLorg/joml/Vector4f;)V");
    }

    @Test
    void testModifyConstantReplacedByField() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/GuiMixin",
            "raiseVehicleHearts",
            assertType(),
            assertTargetMethod(),
            assertInjectionPoint()
        );
    }

    @Override
    protected LoadResult load(String className, List<String> allowedMethods) throws Exception {
        ClassNode patched = loadClass(className);
        patched.methods.removeIf(m -> !allowedMethods.contains(m.name) && allowedMethods.stream().noneMatch(name -> m.name.startsWith("lambda$" + name + "$")));
        patcher.process(patched);
        return new LoadResult(patchEnvironment, patched, loadClass(className));
    }
}
