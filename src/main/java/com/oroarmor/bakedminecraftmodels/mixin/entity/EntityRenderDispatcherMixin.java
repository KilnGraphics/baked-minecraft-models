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

package com.oroarmor.bakedminecraftmodels.mixin.entity;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils;
import com.oroarmor.bakedminecraftmodels.model.InstancedRenderDispatcher;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils.*;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements InstancedRenderDispatcher {

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", shift = At.Shift.AFTER), cancellable = true)
    private <E extends Entity> void queueRender(E entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        matrices.pop();
        ci.cancel();
    }

    public void renderQueues() {
        GlobalModelUtils.createPartPboIfNeeded();
        GlobalModelUtils.createModelPboIfNeeded();

        long currentPartSyncObject = PART_PBO.getCurrentSyncObject();

        if (currentPartSyncObject != MemoryUtil.NULL) {
            int waitReturn = GL32C.GL_UNSIGNALED;
            while (waitReturn != GL32C.GL_ALREADY_SIGNALED && waitReturn != GL32C.GL_CONDITION_SATISFIED) {
                waitReturn = GL32C.glClientWaitSync(currentPartSyncObject, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 1);
            }
        }

        long sectionStartPos = PART_PBO.getCurrentSection() * PART_PBO.getSectionSize();

        bakingData.writeToPbos(MODEL_PBO, PART_PBO);


        GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, PART_PBO.getName());
        GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, PART_PBO.getCurrentSection() * PART_PBO.getSectionSize(), PART_PBO.getSectionSize());

        GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, MODEL_PBO.getName());
        GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, MODEL_PBO.getCurrentSection() * MODEL_PBO.getSectionSize(), MODEL_PBO.getSectionSize());

        GlobalModelUtils.bakingData.reset();

        if (currentPartSyncObject != MemoryUtil.NULL) {
            GL32C.glDeleteSync(currentPartSyncObject);
        }
        pbo.setCurrentSyncObject(GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));

        GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 1, pbo.getName(), sectionStartPos, pbo.getSectionSize());

        pbo.nextSection();

        RenderLayer layer = ((RenderLayerCreatedBufferBuilder) getCurrentPassNestedBuilder()).getRenderLayer();
        if (layer == null) {
            throw new RuntimeException("RenderLayer provided with BufferBuilder is null");
        }

        if (this.vertexCount != 0) {
            layer.startDrawing();
            RenderSystem.assertThread(RenderSystem::isOnRenderThread);
            BufferRenderer.unbindAll();

            for(int i = 0; i < 12; ++i) {
                int j = RenderSystem.getShaderTexture(i);
                shader.addSampler("Sampler" + i, j);
            }

            if (shader.modelViewMat != null) {
                shader.modelViewMat.set(viewMatrix);
            }

            if (shader.projectionMat != null) {
                shader.projectionMat.set(projectionMatrix);
            }

            if (shader.colorModulator != null) {
                shader.colorModulator.set(RenderSystem.getShaderColor());
            }

            if (shader.fogStart != null) {
                shader.fogStart.set(RenderSystem.getShaderFogStart());
            }

            if (shader.fogEnd != null) {
                shader.fogEnd.set(RenderSystem.getShaderFogEnd());
            }

            if (shader.fogColor != null) {
                shader.fogColor.set(RenderSystem.getShaderFogColor());
            }

            if (shader.textureMat != null) {
                shader.textureMat.set(RenderSystem.getTextureMatrix());
            }

            if (shader.gameTime != null) {
                shader.gameTime.set(RenderSystem.getShaderGameTime());
            }

            if (shader.screenSize != null) {
                Window window = MinecraftClient.getInstance().getWindow();
                shader.screenSize.set((float)window.getFramebufferWidth(), (float)window.getFramebufferHeight());
            }

            if (shader.lineWidth != null && (this.drawMode == VertexFormat.DrawMode.LINES || this.drawMode == VertexFormat.DrawMode.LINE_STRIP)) {
                shader.lineWidth.set(RenderSystem.getShaderLineWidth());
            }

            RenderSystem.setupShaderLights(shader);
            this.bindVertexArray();
            this.bind();
            this.getElementFormat().startDrawing();
            shader.bind();
            RenderSystem.drawElements(this.drawMode.mode, this.vertexCount, this.vertexFormat.count);
            shader.unbind();
            this.getElementFormat().endDrawing();
            unbind();
            VertexBuffer.unbindVertexArray();
            layer.endDrawing();
        }
        getBakedVertices().setShader(getCurrentPassOriginalStack().peek().getModel(), RenderSystem.getProjectionMatrix(), BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL);
    }
}
