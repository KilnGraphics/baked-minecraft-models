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
import com.oroarmor.bakedminecraftmodels.mixin.buffer.VertexBufferAccessor;
import com.oroarmor.bakedminecraftmodels.model.InstancedRenderDispatcher;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
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
        if (bakingData.getCurrentModelTypeData() == null) return;
        int instanceCount = bakingData.getCurrentModelTypeData().getInstanceCount();
        if (instanceCount <= 0) return;

        SectionedPbo partPbo = getOrCreatePartPbo();
        SectionedPbo modelPbo = getOrCreateModelPbo();

        long currentPartSyncObject = SYNC_OBJECTS.getCurrentSyncObject();

        if (currentPartSyncObject != MemoryUtil.NULL) {
            int waitReturn = GL32C.GL_UNSIGNALED;
            while (waitReturn != GL32C.GL_ALREADY_SIGNALED && waitReturn != GL32C.GL_CONDITION_SATISFIED) {
                waitReturn = GL32C.glClientWaitSync(currentPartSyncObject, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 1);
            }
        }

        long partSectionStartPos = partPbo.getCurrentSection() * partPbo.getSectionSize();
        long modelSectionStartPos = modelPbo.getCurrentSection() * modelPbo.getSectionSize();

        bakingData.writeToPbos(modelPbo, partPbo);

        GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, partPbo.getName());
        GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, partSectionStartPos, partPbo.getSectionSize());

        GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, modelPbo.getName());
        GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, modelSectionStartPos, modelPbo.getSectionSize());

        RenderLayer layer = bakingData.getCurrentModelTypeData().getRenderLayer();
        if (layer == null) {
            throw new RuntimeException("RenderLayer provided with BufferBuilder is null");
        }
        VertexBuffer vertexBuffer = bakingData.getCurrentModelTypeData().getModel().getBakedVertices();
        VertexBufferAccessor vertexBufferAccessor = (VertexBufferAccessor) vertexBuffer;
        int vertexCount = vertexBufferAccessor.getVertexCount();
        VertexFormat.DrawMode drawMode = vertexBufferAccessor.getDrawMode();
        Shader shader = BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL;
        if (shader == null) {
            throw new IllegalStateException("Smart entity shader is null");
        }

        bakingData.reset();

        if (currentPartSyncObject != MemoryUtil.NULL) {
            GL32C.glDeleteSync(currentPartSyncObject);
        }
        SYNC_OBJECTS.setCurrentSyncObject(GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));

        GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 1, partPbo.getName(), partSectionStartPos, partPbo.getSectionSize());
        GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 2, modelPbo.getName(), modelSectionStartPos, modelPbo.getSectionSize());

        partPbo.nextSection();
        modelPbo.nextSection();
        SYNC_OBJECTS.nextSection();

        if (vertexCount != 0) {
            layer.startDrawing();
            RenderSystem.assertThread(RenderSystem::isOnRenderThread);
            BufferRenderer.unbindAll();

            for(int i = 0; i < 12; ++i) {
                int j = RenderSystem.getShaderTexture(i);
                shader.addSampler("Sampler" + i, j);
            }

            if (shader.projectionMat != null) {
                shader.projectionMat.set(RenderSystem.getProjectionMatrix());
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

            if (shader.lineWidth != null && (drawMode == VertexFormat.DrawMode.LINES || drawMode == VertexFormat.DrawMode.LINE_STRIP)) {
                shader.lineWidth.set(RenderSystem.getShaderLineWidth());
            }

            RenderSystem.setupShaderLights(shader);
            vertexBufferAccessor.invokeBindVertexArray();
            vertexBufferAccessor.invokeBind();
            vertexBuffer.getElementFormat().startDrawing();
            shader.bind();
            GL31C.glDrawElementsInstanced(drawMode.mode, vertexCount, vertexBufferAccessor.getVertexFormat().count, MemoryUtil.NULL, instanceCount);
            shader.unbind();
            vertexBuffer.getElementFormat().endDrawing();
            VertexBuffer.unbind();
            VertexBuffer.unbindVertexArray();
            layer.endDrawing();
        }
    }
}
