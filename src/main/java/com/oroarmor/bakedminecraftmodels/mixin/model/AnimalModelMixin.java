package com.oroarmor.bakedminecraftmodels.mixin.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.SpriteTexturedVertexConsumerAccessor;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalModel.class)
public abstract class AnimalModelMixin {
    @Unique
    private VertexBuffer vertexBuffer;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void useVbo(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (vertexBuffer != null) {
            BufferVertexConsumer parent = vertices instanceof SpriteTexturedVertexConsumer ?
                    (BufferVertexConsumer) ((SpriteTexturedVertexConsumerAccessor) vertices).getParent() :
                    (BufferVertexConsumer) vertices;
            if (((BufferBuilderAccessor) parent).getFormat() == BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT) {
                BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("Color").set(new Vector4f(red, green, blue, alpha));
                BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV1").set(overlay & 65535, overlay >> 16 & 65535);
                BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV2").set(light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295), light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295));

                vertexBuffer.setShader(matrices.peek().getModel(), RenderSystem.getProjectionMatrix(), BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL);
            }
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    public void storeIntoVbo(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (vertexBuffer == null) {
            BufferVertexConsumer parent = vertices instanceof SpriteTexturedVertexConsumer ?
                    (BufferVertexConsumer) ((SpriteTexturedVertexConsumerAccessor) vertices).getParent() :
                    (BufferVertexConsumer) vertices;
            if (((BufferBuilderAccessor) parent).getFormat().equals(BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT)) {
                vertexBuffer = new VertexBuffer();
                vertexBuffer.upload((BufferBuilder) parent);
            }
        }
    }
}
