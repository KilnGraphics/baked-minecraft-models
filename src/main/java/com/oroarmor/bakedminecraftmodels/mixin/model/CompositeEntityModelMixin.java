package com.oroarmor.bakedminecraftmodels.mixin.model;

import com.oroarmor.bakedminecraftmodels.model.VboModel;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.CompositeEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CompositeEntityModel.class)
public abstract class CompositeEntityModelMixin implements VboModel {

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
    private MatrixStack bmm$tryReplaceMatrixStack(MatrixStack existingStack) {
        return tryReplaceMatrixStack(existingStack);
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
