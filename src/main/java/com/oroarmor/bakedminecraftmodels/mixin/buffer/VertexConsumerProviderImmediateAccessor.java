package com.oroarmor.bakedminecraftmodels.mixin.buffer;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;

@Mixin(VertexConsumerProvider.Immediate.class)
public interface VertexConsumerProviderImmediateAccessor {
    @Accessor
    Optional<RenderLayer> getCurrentLayer();
}
