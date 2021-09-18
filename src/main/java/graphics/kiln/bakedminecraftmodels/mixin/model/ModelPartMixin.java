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

package graphics.kiln.bakedminecraftmodels.mixin.model;

import graphics.kiln.bakedminecraftmodels.access.BakeablePart;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.vertex.SmartBufferBuilderWrapper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements BakeablePart {

    @Unique
    private int bmm$id;

    @Unique
    private boolean bmm$usingSmartRenderer;

    @Shadow
    public boolean visible;

    @Shadow protected abstract void renderCuboids(MatrixStack.Entry entry, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha);

    @Shadow public float roll;

    @Shadow public float pitch;

    @Shadow public float yaw;

    @Shadow @Final private List<ModelPart.Cuboid> cuboids;

    @Shadow @Final private Map<String, ModelPart> children;

    @Shadow public abstract void rotate(MatrixStack matrix);

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

            float rot00 = cy * cz;
            float rot01 = (sx * sy * cz) - (cx * sz);
            float rot02 = (cx * sy * cz) + (sx * sz);
            float rot10 = cy * sz;
            float rot11 = (sx * sy * sz) + (cx * cz);
            float rot12 = (cx * sy * sz) - (sx * cz);
            float rot20 = -sy;
            float rot21 = sx * cy;
            float rot22 = cx * cy;

            float new00 = model.a00 * rot00 + model.a01 * rot10 + model.a02 * rot20;
            float new01 = model.a00 * rot01 + model.a01 * rot11 + model.a02 * rot21;
            float new02 = model.a00 * rot02 + model.a01 * rot12 + model.a02 * rot22;
            float new10 = model.a10 * rot00 + model.a11 * rot10 + model.a12 * rot20;
            float new11 = model.a10 * rot01 + model.a11 * rot11 + model.a12 * rot21;
            float new12 = model.a10 * rot02 + model.a11 * rot12 + model.a12 * rot22;
            float new20 = model.a20 * rot00 + model.a21 * rot10 + model.a22 * rot20;
            float new21 = model.a20 * rot01 + model.a21 * rot11 + model.a22 * rot21;
            float new22 = model.a20 * rot02 + model.a21 * rot12 + model.a22 * rot22;
            float new30 = model.a30 * rot00 + model.a31 * rot10 + model.a32 * rot20;
            float new31 = model.a30 * rot01 + model.a31 * rot11 + model.a32 * rot21;
            float new32 = model.a30 * rot02 + model.a31 * rot12 + model.a32 * rot22;

            Matrix3f normal = currentStackEntry.getNormal();
            model.a00 = normal.a00 = new00;
            model.a01 = normal.a01 = new01;
            model.a02 = normal.a02 = new02;
            model.a10 = normal.a10 = new10;
            model.a11 = normal.a11 = new11;
            model.a12 = normal.a12 = new12;
            model.a20 = normal.a20 = new20;
            model.a21 = normal.a21 = new21;
            model.a22 = normal.a22 = new22;
            model.a30 = new30;
            model.a31 = new31;
            model.a32 = new32;

            GlobalModelUtils.bakingData.addPartMatrix(bmm$id, this.visible ? model : null); // TODO: does this method ever get called when the part is not visible?
            ci.cancel();
        }
    }

    /**
     * Used to manipulate visibility, matrices, and drawing
     *
     * @author burgerdude
     */
    @Overwrite
    public void render(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha) {
        if (!this.cuboids.isEmpty() || !this.children.isEmpty()) {
            boolean rotateOnly = vertexConsumer == null;
            bmm$usingSmartRenderer = (rotateOnly || vertexConsumer instanceof SmartBufferBuilderWrapper) && MinecraftClient.getInstance().getWindow() != null;

            // force render when constructing the vbo
            if (this.visible || (!rotateOnly && bmm$usingSmartRenderer)) {
                matrices.push();

                this.rotate(matrices);

                if (bmm$usingSmartRenderer) {
                    if (!rotateOnly) {
                        ((SmartBufferBuilderWrapper) vertexConsumer).setId(this.getId());
                        this.renderCuboids(GlobalModelUtils.IDENTITY_STACK_ENTRY, vertexConsumer, light, overlay, red, green, blue, alpha);
                    }
                } else {
                    this.renderCuboids(matrices.peek(), vertexConsumer, light, overlay, red, green, blue, alpha);
                }

                for(ModelPart modelPart : this.children.values()) {
                    modelPart.render(matrices, vertexConsumer, light, overlay, red, green, blue, alpha);
                }

                matrices.pop();
            } else {
                recurseSetNullMatrix((ModelPart) (Object) this);
            }
        }
    }

    private static void recurseSetNullMatrix(ModelPart modelPart) {
        if ((Object) modelPart instanceof ModelPartMixin modelPartMixin) {
            GlobalModelUtils.bakingData.addPartMatrix(modelPartMixin.getId(), null);
            for (ModelPart child : modelPartMixin.children.values()) {
                recurseSetNullMatrix(child);
            }
        }
    }

}
