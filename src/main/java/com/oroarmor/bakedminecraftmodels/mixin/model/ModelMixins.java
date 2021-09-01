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

import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedVertexConsumer;
import com.oroarmor.bakedminecraftmodels.data.ModelInstanceData;
import com.oroarmor.bakedminecraftmodels.data.ModelType;
import com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils;
import com.oroarmor.bakedminecraftmodels.model.VboBackedModel;
import net.minecraft.client.MinecraftClient;
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
public abstract class ModelMixins implements VboBackedModel {

    @Unique
    @Nullable
    private VertexBuffer bmm$bakedVertices;

    @Override
    @Unique
    public VertexBuffer getBakedVertices() {
        return bmm$bakedVertices;
    }

    @Unique
    private void setBakedVertices(VertexBuffer bmm$bakedVertices) {
        this.bmm$bakedVertices = bmm$bakedVertices;
    }

    @Unique
    private boolean bmm$currentPassBakeable;

    @Unique
    private BufferBuilder bmm$currentPassNestedBuilder;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void updateCurrentPass(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        bmm$currentPassNestedBuilder = GlobalModelUtils.getNestedBufferBuilder(vertexConsumer);
        bmm$currentPassBakeable = GlobalModelUtils.isSmartBufferBuilder(bmm$currentPassNestedBuilder) && MinecraftClient.getInstance().getWindow() != null;
        if (bmm$currentPassBakeable) {
            GlobalModelUtils.bakingData.tryCreateCurrentModelTypeData(new ModelType(this, null));
            GlobalModelUtils.bakingData.getCurrentModelTypeData().setRenderLayer(((RenderLayerCreatedVertexConsumer) bmm$currentPassNestedBuilder).getRenderLayer());
            GlobalModelUtils.bakingData.getCurrentModelTypeData().createCurrentModelInstanceData();
            GlobalModelUtils.bakingData.getCurrentModelTypeData().getCurrentModelInstanceData().setBaseModelViewMatrix(matrices.peek().getModel());
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private VertexConsumer disableImmediateRendering(VertexConsumer existingConsumer) {
        if (getBakedVertices() != null && bmm$currentPassBakeable) {
            return null;
        } else {
            return existingConsumer;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void createVbo(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (getBakedVertices() == null && bmm$currentPassBakeable) {
//            ((RenderLayerCreatedVertexConsumer) bmm$currentPassNestedBuilder).getRenderLayer().draw(bmm$currentPassNestedBuilder, 0, 0, 0);
            bmm$currentPassNestedBuilder.end(); // FIXME: this is weird
            setBakedVertices(new VertexBuffer());
            getBakedVertices().upload(bmm$currentPassNestedBuilder);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void setModelInstanceData(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (getBakedVertices() != null && bmm$currentPassBakeable) {
            ModelInstanceData modelInstanceData = GlobalModelUtils.bakingData.getCurrentModelTypeData().getCurrentModelInstanceData();
            modelInstanceData.setLight(light);
            modelInstanceData.setOverlay(overlay);
            modelInstanceData.setColor(red, green, blue, alpha);
        }
    }

}
