package com.gtnewhorizons.galaxia.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockAir;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.galaxia.registry.block.AirPressurizable;

/**
 * Mixin to change class of vanilla air
 */
@Mixin(Block.class)
public abstract class AirMixin {

    @Redirect(method = "registerBlocks", at = @At(value = "NEW", target = "net/minecraft/block/BlockAir"))
    private static BlockAir galaxia$replaceAirBlock() {
        return new AirPressurizable();
    }
}
