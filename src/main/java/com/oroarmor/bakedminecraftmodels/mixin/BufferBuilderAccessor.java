package com.oroarmor.bakedminecraftmodels.mixin;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor
    VertexFormat getFormat();

    @Accessor
    ByteBuffer getBuffer();

    @Accessor
    void setBuffer(ByteBuffer buffer);
}
