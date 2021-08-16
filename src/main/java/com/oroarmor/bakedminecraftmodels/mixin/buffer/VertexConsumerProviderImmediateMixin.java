package com.oroarmor.bakedminecraftmodels.mixin.buffer;

import java.util.Optional;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

@Mixin(VertexConsumerProvider.Immediate.class)
public class VertexConsumerProviderImmediateMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    public void attachRenderLayerToBuffer(RenderLayer renderLayer, CallbackInfoReturnable<VertexConsumer> cir) {
        BufferBuilder builder = BakedMinecraftModels.getNestedBufferBuilder(cir.getReturnValue());
        ((RenderLayerCreatedBufferBuilder) builder).setRenderLayer(renderLayer);
    }
}
