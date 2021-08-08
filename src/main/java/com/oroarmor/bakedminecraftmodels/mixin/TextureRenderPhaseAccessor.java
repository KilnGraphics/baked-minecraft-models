package com.oroarmor.bakedminecraftmodels.mixin;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.util.Identifier;

@Mixin(targets = "net.minecraft.client.render.RenderPhase$Texture")
public interface TextureRenderPhaseAccessor {
    @Accessor
    Optional<Identifier> getId();
}
