package com.oroarmor.bakedminecraftmodels.access;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.RenderLayer;

public interface RenderLayerCreatedBufferBuilder {
    void setRenderLayer(RenderLayer layer);
    @Nullable RenderLayer getRenderLayer();
}
