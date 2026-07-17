package org.sinytra.adapter.test.mc_26_1_2.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Equivalent of the Origins Classes efficient-repairs mixin used to verify
 * retargeting an integer {@link ModifyConstant} injector for NeoForge.
 */
@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {
    @ModifyConstant(
        method = "createResult",
        constant = @Constant(intValue = 4, ordinal = 0)
    )
    private int halfRepairMaterialCost(int original) {
        return original / 2;
    }

    @ModifyConstant(
        method = "createResultInternal",
        constant = @Constant(intValue = 4)
    )
    private int halfRepairMaterialCostExpected(int original) {
        return original / 2;
    }
}
