package com.oroarmor.bakedminecraftmodels.mixin.renderlayer;

import com.oroarmor.bakedminecraftmodels.vertex.RenderLayerContainer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin implements RenderLayerContainer {

    private RenderLayer bmm$renderLayer;

    @Override
    public RenderLayer getRenderLayer() {
        return bmm$renderLayer;
    }

    @Override
    public void setRenderLayer(RenderLayer renderLayer) {
        bmm$renderLayer = renderLayer;
    }
}
