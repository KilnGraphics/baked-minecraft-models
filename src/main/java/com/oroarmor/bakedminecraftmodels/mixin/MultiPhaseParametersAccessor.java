package com.oroarmor.bakedminecraftmodels.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.RenderPhase;

@Mixin(targets = "net.minecraft.client.render.RenderLayer$MultiPhaseParameters")
public interface MultiPhaseParametersAccessor {
    @Accessor
    RenderPhase.TextureBase getTexture();
}
