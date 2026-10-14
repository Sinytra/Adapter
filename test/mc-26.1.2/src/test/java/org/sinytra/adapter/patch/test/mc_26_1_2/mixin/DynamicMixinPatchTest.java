package org.sinytra.adapter.patch.test.mc_26_1_2.mixin;

import com.mojang.logging.LogUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.sinytra.adapter.api.ClassLookup;
import org.sinytra.adapter.api.DynamicPatches;
import org.sinytra.adapter.api.PatchEnvironment;
import org.sinytra.adapter.api.Patcher;
import org.sinytra.adapter.api.RefmapHolder;
import org.sinytra.adapter.model.constant.MixinAnnotations;
import org.sinytra.adapter.patch.test_main.mixin.MinecraftMixinPatchTest;
import org.sinytra.adapter.util.AdapterUtil;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;

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

    @Override
    protected LoadResult load(String className, List<String> allowedMethods) throws Exception {
        ClassNode patched = loadClass(className);
        patched.methods.removeIf(m -> !allowedMethods.contains(m.name) && allowedMethods.stream().noneMatch(name -> m.name.startsWith("lambda$" + name + "$")));
        patcher.process(patched);
        return new LoadResult(patchEnvironment, patched, loadClass(className));
    }
}
