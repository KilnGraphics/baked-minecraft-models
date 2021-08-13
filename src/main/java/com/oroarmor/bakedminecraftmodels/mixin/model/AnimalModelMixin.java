/*
 * MIT License
 *
 * Copyright (c) 2021 OroArmor (Eli Orona), Blaze4D
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oroarmor.bakedminecraftmodels.mixin.model;

import java.util.Optional;

import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.SpriteTexturedVertexConsumerAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferVertexConsumer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.util.math.MatrixStack;

@Mixin(AnimalModel.class)
public abstract class AnimalModelMixin {
    @Unique
    private VertexBuffer vertexBuffer;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void useVbo(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (vertexBuffer != null) {
            BufferBuilderAccessor parent = (BufferBuilderAccessor) (vertices instanceof SpriteTexturedVertexConsumer ?
                    ((SpriteTexturedVertexConsumerAccessor) vertices).getParent() : vertices);
            if (parent.getFormat() == BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT && BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL != null) {
                BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("Color").set(red, green, blue, alpha);
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
            BufferVertexConsumer parent = (BufferVertexConsumer) (vertices instanceof SpriteTexturedVertexConsumer ?
                    ((SpriteTexturedVertexConsumerAccessor) vertices).getParent() : vertices);
            if (((BufferBuilderAccessor) parent).getFormat().equals(BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT)) {
                vertexBuffer = new VertexBuffer();
                vertexBuffer.upload((BufferBuilder) parent);
            }
        }
    }
}
