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
import com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements BakeablePart {

    @Unique
    private int bmm$id;

    @Unique
    private boolean bmm$usingSmartRenderer;

    @Shadow
    @Final
    private List<ModelPart.Cuboid> cuboids;

    @Shadow
    public boolean visible;

    @Override
    public void setId(int id) {
        bmm$id = id;
        cuboids.forEach(cuboid -> ((BakeablePart) cuboid).setId(bmm$id));
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @Inject(method = "rotate", at = @At("HEAD"))
    public void pushMatrixStack(MatrixStack matrices, CallbackInfo ci) {
        if (bmm$usingSmartRenderer) {
//            matrices.push();
        }
    }

    @Inject(method = "rotate", at = @At("TAIL"))
    public void setSsboRotation(MatrixStack matrices, CallbackInfo ci) {
        if (bmm$usingSmartRenderer) {
            while (GlobalModelUtils.currentMatrices.size() <= bmm$id) {
                GlobalModelUtils.currentMatrices.add(null);
            }
            if (this.visible) {
                GlobalModelUtils.currentMatrices.set(bmm$id, matrices.peek().getModel());
            }
//            matrices.pop();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/util/math/MatrixStack;push()V"))
    public void useVertexBufferRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        bmm$usingSmartRenderer = GlobalModelUtils.isSmartBufferBuilder(GlobalModelUtils.getNestedBufferBuilder(vertices));
    }

}
