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

import com.oroarmor.bakedminecraftmodels.access.BakeablePart;
import com.oroarmor.bakedminecraftmodels.data.MatrixList;
import com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils;
import com.oroarmor.bakedminecraftmodels.vertex.SmartBufferBuilderWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements BakeablePart {

    @Unique
    private int bmm$id;

    @Unique
    private boolean bmm$usingSmartRenderer;

    @Unique
    private boolean bmm$rotateOnly;

    @Shadow
    public boolean visible;

    @Shadow protected abstract void renderCuboids(MatrixStack.Entry entry, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha);

    @Shadow public float roll;

    @Shadow public float pitch;

    @Shadow public float yaw;

    @Shadow public abstract boolean isEmpty();

    @Override
    public void setId(int id) {
        bmm$id = id;
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @Inject(method = "rotate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(DDD)V", shift = At.Shift.AFTER), cancellable = true)
    public void setSsboRotation(MatrixStack matrices, CallbackInfo ci) {
        if (bmm$usingSmartRenderer) {
            MatrixStack.Entry currentStackEntry = matrices.peek();
            Matrix4f model = currentStackEntry.getModel();

            float sx = MathHelper.sin(pitch);
            float cx = MathHelper.cos(pitch);
            float sy = MathHelper.sin(yaw);
            float cy = MathHelper.cos(yaw);
            float sz = MathHelper.sin(roll);
            float cz = MathHelper.cos(roll);

            Matrix4f rotMat = new Matrix4f();
            rotMat.a00 = cy * cz;
            rotMat.a01 = (sx * sy * cz) - (cx * sz);
            rotMat.a02 = (cx * sy * cz) + (sx * sz);
            rotMat.a10 = cy * sz;
            rotMat.a11 = (sx * sy * sz) + (cx * cz);
            rotMat.a12 = (cx * sy * sz) - (sx * cz);
            rotMat.a20 = -sy;
            rotMat.a21 = sx * cy;
            rotMat.a22 = cx * cy;
            rotMat.a33 = 1.0F;

            model.multiply(rotMat);

            Matrix3f normal = currentStackEntry.getNormal();
            normal.a00 = model.a00;
            normal.a01 = model.a01;
            normal.a02 = model.a02;
            normal.a10 = model.a10;
            normal.a11 = model.a11;
            normal.a12 = model.a12;
            normal.a20 = model.a20;
            normal.a21 = model.a21;
            normal.a22 = model.a22;

            MatrixList matrixList = GlobalModelUtils.bakingData.getCurrentModelTypeData().getCurrentModelInstanceData().getMatrixList();
            if (this.visible) {
                matrixList.add(bmm$id, model);
            }
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/util/math/MatrixStack;push()V"))
    public void useVertexBufferRender(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        bmm$rotateOnly = vertexConsumer == null;
        bmm$usingSmartRenderer = (bmm$rotateOnly || vertexConsumer instanceof SmartBufferBuilderWrapper) && MinecraftClient.getInstance().getWindow() != null;
    }

    @Redirect(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/ModelPart;renderCuboids(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V"))
    public void forceRotateOrChangeMatrixStack(ModelPart modelPart, MatrixStack.Entry entry, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha) {
        if (bmm$usingSmartRenderer) {
            if (!bmm$rotateOnly) {
                ((SmartBufferBuilderWrapper) vertexConsumer).setId(this.getId());
                this.renderCuboids(GlobalModelUtils.IDENTITY_STACK_ENTRY, vertexConsumer, light, overlay, red, green, blue, alpha);
            }
        } else {
            this.renderCuboids(entry, vertexConsumer, light, overlay, red, green, blue, alpha);
        }
    }

}
