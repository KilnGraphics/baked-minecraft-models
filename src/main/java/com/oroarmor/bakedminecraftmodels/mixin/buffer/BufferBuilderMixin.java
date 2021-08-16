package com.oroarmor.bakedminecraftmodels.mixin.buffer;

import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin implements RenderLayerCreatedBufferBuilder {
    @Unique
    private RenderLayer bmm$renderLayer;

    @Override
    public void setRenderLayer(RenderLayer layer) {
        bmm$renderLayer = layer;
    }

    @Override
    public RenderLayer getRenderLayer() {
        return bmm$renderLayer;
    }
}
