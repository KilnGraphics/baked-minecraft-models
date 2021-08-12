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

import java.util.List;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;
import com.oroarmor.bakedminecraftmodels.access.ModelID;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.SpriteTexturedVertexConsumerAccessor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferVertexConsumer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vector4f;

@Mixin(ModelPart.class)
public class ModelPartMixin implements ModelID {
    @Unique
    private int bmm$id;

    @Shadow
    @Final
    private List<ModelPart.Cuboid> cuboids;

    @Override
    public void setId(int id) {
        bmm$id = id;
        cuboids.forEach(cuboid -> ((ModelID) cuboid).setId(bmm$id));
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    public void setShaderUniforms(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        BufferVertexConsumer parent = vertices instanceof SpriteTexturedVertexConsumer ?
                (BufferVertexConsumer) ((SpriteTexturedVertexConsumerAccessor) vertices).getParent() :
                (BufferVertexConsumer) vertices;
        if (bmm$id == 0 && ((BufferBuilderAccessor) parent).getFormat() == BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT) {
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("Color").set(new Vector4f(red, green, blue, alpha));
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV1").set(overlay & 65535, overlay >> 16 & 65535);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV2").set(light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295), light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295));
        }
    }
}