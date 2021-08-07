package com.oroarmor.bakedminecraftmodels.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;

@Mixin(SpriteTexturedVertexConsumer.class)
public interface SpriteTexturedVertexConsumerAccessor {
    @Accessor
    VertexConsumer getParent();
}
