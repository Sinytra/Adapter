package org.sinytra.adapter.patch.test.mc_26_1_2.mixin;

import com.mojang.logging.LogUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.tree.ClassNode;
import org.sinytra.adapter.env.ctx.PatchEnvironment;
import org.sinytra.adapter.env.ctx.RefmapHolder;
import org.sinytra.adapter.patch.DynamicPatches;
import org.sinytra.adapter.patch.Patcher;
import org.sinytra.adapter.patch.test_main.mixin.MinecraftMixinPatchTest;
import org.sinytra.adapter.transform.patch.MethodPatch;
import org.sinytra.adapter.types.FieldTypeUsageTransformer;
import org.sinytra.adapter.util.provider.ClassLookup;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.FabricUtil;

import java.util.List;

import static org.sinytra.adapter.env.util.MixinAnnotations.MODIFY_CONST;

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
            .methodTransformers(DynamicPatches.methodTransformers(List.of(
                MethodPatch.builder()
                    .targetClass("net/minecraft/world/inventory/AnvilMenu")
                    .targetMethod("createResult")
                    .targetMixinType(MODIFY_CONST)
                    .modifyTarget("createResultInternal")
                    .build()
            )))
            .build();
    }

    @AfterAll
    static void postTest() {
        LOGGER.info("Complete report:\n\n{}", patchEnvironment.auditTrail().getCompleteReport());
    }

    @Test
    void testExtractUsingCodePath() throws Exception {
        assertSameCodeExtracted(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/LivingEntityMixin",
            "playSoundCorrectlyForBlocks",
            "org/sinytra/adapter/test/mc_26_1_2/mixin/adapter_generated_IBlockExtension",
            assertTargetMethod()
        );
    }

    /**
     * Reproduces Origins Classes' efficient-repairs mixin, whose integer
     * {@code @ModifyConstant} target moves from {@code AnvilMenu#createResult}
     * to NeoForge's {@code AnvilMenu#createResultInternal}.
     */
    @Test
    void testModifyConstantTarget() throws Exception {
        assertSameCode(
            "org/sinytra/adapter/test/mc_26_1_2/mixin/AnvilMenuMixin",
            "halfRepairMaterialCost",
            assertTargetMethod(),
            assertTargetsConstant()
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
