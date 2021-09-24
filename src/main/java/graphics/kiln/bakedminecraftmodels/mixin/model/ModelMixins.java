/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.model;

import graphics.kiln.bakedminecraftmodels.BakedMinecraftModelsRenderLayerManager;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.vertex.RenderLayerContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.block.entity.SignBlockEntityRenderer;
import net.minecraft.client.render.entity.EnderDragonEntityRenderer;
import net.minecraft.client.render.entity.model.*;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
        TurtleEntityModel.class
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
    private boolean bmm$currentPassBakeable;

    @Unique
    private VertexFormat.DrawMode bmm$drawMode;

    @Unique
    private VertexFormat bmm$vertexFormat;

    @Unique
    protected boolean bmm$childBakeable() { // this will be overridden by the lowest in the hierarchy as long as it's not private
        return bmm$currentPassBakeable;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void updateCurrentPass(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
       if (!bmm$childBakeable() && GlobalModelUtils.getNestedBufferBuilder(vertexConsumer) instanceof RenderLayerContainer renderLayerContainer) {
            RenderLayer convertedRenderLayer = BakedMinecraftModelsRenderLayerManager.tryDeriveSmartRenderLayer(renderLayerContainer.getRenderLayer());
            bmm$currentPassBakeable = convertedRenderLayer != null && MinecraftClient.getInstance().getWindow() != null;
            if (bmm$currentPassBakeable) {
                bmm$drawMode = convertedRenderLayer.getDrawMode();
                bmm$vertexFormat = convertedRenderLayer.getVertexFormat();
                GlobalModelUtils.bakingData.beginInstance(this, convertedRenderLayer, matrices.peek());
            }
        } else {
            bmm$currentPassBakeable = false;
            bmm$drawMode = null;
            bmm$vertexFormat = null;
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private VertexConsumer changeVertexConsumer(VertexConsumer existingConsumer) {
        if (getBakedVertices() != null && bmm$currentPassBakeable) {
            return null;
        } else if (bmm$currentPassBakeable) {
            GlobalModelUtils.VBO_BUFFER_BUILDER.begin(bmm$drawMode, bmm$vertexFormat);
            return GlobalModelUtils.VBO_BUFFER_BUILDER;
        } else {
            return existingConsumer;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void createVbo(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (getBakedVertices() == null && bmm$currentPassBakeable) {
            GlobalModelUtils.VBO_BUFFER_BUILDER.end();
            bmm$bakedVertices = new VertexBuffer();
            getBakedVertices().upload(GlobalModelUtils.VBO_BUFFER_BUILDER.getInternalBufferBuilder());
            GlobalModelUtils.VBO_BUFFER_BUILDER.clear();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void setModelInstanceData(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (getBakedVertices() != null && bmm$currentPassBakeable) {
            GlobalModelUtils.bakingData.endInstance(red, green, blue, alpha, overlay, light);
            bmm$currentPassBakeable = false; // we want this to be false by default when we start at the top again
        }
    }

}
