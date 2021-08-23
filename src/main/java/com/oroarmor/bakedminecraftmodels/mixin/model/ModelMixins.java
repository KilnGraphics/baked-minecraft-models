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

import com.oroarmor.bakedminecraftmodels.model.VboModel;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// FIXME: make sure when a model's render calls super, only do the procedure once and in the outer-most injection
@Mixin({AnimalModel.class,
        BookModel.class, // TODO OPT: inject into renderBook instead of render so it works on block models
        CompositeEntityModel.class,
        EnderDragonEntityRenderer.DragonEntityModel.class,
        DragonHeadEntityModel.class,
        LlamaEntityModel.class,
        RabbitEntityModel.class,
        ShieldEntityModel.class,
        SignBlockEntityRenderer.SignModel.class,
        SinglePartEntityModel.class,
        SkullEntityModel.class,
        TintableAnimalModel.class,
        TintableCompositeModel.class,
        TridentEntityModel.class, // FIXME: enchantment glint uses dual
        TurtleEntityModel.class // FIXME: this is broken
})
public abstract class ModelMixins implements VboModel {

    @Unique
    @Nullable
    private VertexBuffer bmm$bakedVertices;

    @Unique
    @Override
    public VertexBuffer getBakedVertices() {
        return bmm$bakedVertices;
    }

    @Unique
    @Override
    public void setBakedVertices(VertexBuffer vertexBuffer) {
        bmm$bakedVertices = vertexBuffer;
    }

    @Unique
    private boolean bmm$currentPassBakeable;

    @Unique
    @Override
    public boolean isCurrentPassBakeable() {
        return bmm$currentPassBakeable;
    }

    @Unique
    @Override
    public void setCurrentPassBakeable(boolean bakeable) {
        bmm$currentPassBakeable = bakeable;
    }

    @Unique
    private BufferBuilder bmm$currentPassNestedBuilder;

    @Unique
    @Override
    public BufferBuilder getCurrentPassNestedBuilder() {
        return bmm$currentPassNestedBuilder;
    }

    @Unique
    @Override
    public void setCurrentPassNestedBuilder(BufferBuilder nestedBuilder) {
        bmm$currentPassNestedBuilder = nestedBuilder;
    }

    @Unique
    private MatrixStack bmm$currentPassOriginalStack;

    @Unique
    @Override
    public MatrixStack getCurrentPassOriginalStack() {
        return bmm$currentPassOriginalStack;
    }

    @Unique
    @Override
    public void setCurrentPassOriginalStack(MatrixStack originalStack) {
        bmm$currentPassOriginalStack = originalStack;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void bmm$updateCurrentPass(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        updateCurrentPass(matrices, vertexConsumer);
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private VertexConsumer bmm$tryDisableImmediateRendering(VertexConsumer existingConsumer) {
        return tryDisableImmediateRendering(existingConsumer);
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void bmm$tryCreateVbo(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        tryCreateVbo();
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void bmm$tryRenderVbo(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        tryRenderVbo(light, overlay, red, green, blue, alpha);
    }

}
