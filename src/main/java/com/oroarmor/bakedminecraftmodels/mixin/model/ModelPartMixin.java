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

import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;
import com.oroarmor.bakedminecraftmodels.access.ModelID;
import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements ModelID {
    @Unique
    private int bmm$id;

    @Shadow
    @Final
    private List<ModelPart.Cuboid> cuboids;

    @Shadow
    public abstract void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha);

    @Override
    public void setId(int id) {
        bmm$id = id;
        cuboids.forEach(cuboid -> ((ModelID) cuboid).setId(bmm$id));
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @Inject(method = "rotate", at = @At("HEAD"), cancellable = true)
    public void setSsboRotation(MatrixStack matrix, CallbackInfo ci) {
        if(bmm$usingSmartRenderer) {
            ci.cancel();
        }
    }

    @Unique
    private static Object bmm$initialModelPartForBaking = null;

    @Unique
    private static boolean bmm$usingSmartRenderer = false;

    @Unique
    @Nullable
    protected VertexBuffer bmm$bakedVertices;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"), cancellable = true)
    public void useVertexBufferRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        BufferBuilder nestedBufferBuilder = BakedMinecraftModels.getNestedBufferBuilder(vertices);

        bmm$usingSmartRenderer = ((BufferBuilderAccessor) nestedBufferBuilder).getFormat() == BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT;

        if (bmm$initialModelPartForBaking == null && bmm$bakedVertices == null) {
            bmm$initialModelPartForBaking = this;
            if (!nestedBufferBuilder.isBuilding() && bmm$usingSmartRenderer) {
                nestedBufferBuilder.begin(VertexFormat.DrawMode.QUADS, BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
            }
        }

        if (bmm$bakedVertices != null && bmm$usingSmartRenderer) {
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("Color").set(red, green, blue, alpha);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV1").set(overlay & 65535, overlay >> 16 & 65535);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV2").set(light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295), light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295));

            GlUniform normalMatUniform = BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("NormalMat");
            matrices.peek().getNormal().writeColumnMajor(normalMatUniform.getFloatData());

            RenderLayer layer = ((RenderLayerCreatedBufferBuilder) nestedBufferBuilder).getRenderLayer();
            if (layer == null) {
                throw new RuntimeException("This is bad");
            }
            layer.startDrawing();
            bmm$bakedVertices.setShader(matrices.peek().getModel(), RenderSystem.getProjectionMatrix(), BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL);
            layer.endDrawing();
            ci.cancel();
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/util/math/MatrixStack;push()V"))
    public MatrixStack changeMatrixStack(MatrixStack stack) {
        if (bmm$initialModelPartForBaking == this && bmm$bakedVertices == null) {
            return new MatrixStack();
        }
        return stack;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    public void createVertexBufferRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (bmm$bakedVertices == null && bmm$usingSmartRenderer && bmm$initialModelPartForBaking == this) {
            BufferBuilder builder = BakedMinecraftModels.getNestedBufferBuilder(vertices);
            if (MinecraftClient.getInstance().getWindow() != null) {
                builder.end();
                bmm$bakedVertices = new VertexBuffer();
                bmm$bakedVertices.upload(builder);
                this.render(matrices, vertices, light, overlay, red, green, blue, alpha);
            }
        }

        if (bmm$initialModelPartForBaking == this) {
            bmm$initialModelPartForBaking = null;
            bmm$usingSmartRenderer = false;
        }
    }
}
