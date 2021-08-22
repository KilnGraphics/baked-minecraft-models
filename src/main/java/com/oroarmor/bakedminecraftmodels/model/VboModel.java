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

package com.oroarmor.bakedminecraftmodels.model;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryUtil;

public interface VboModel {

    VertexBuffer getBakedVertices();
    void setBakedVertices(VertexBuffer vertexBuffer);

    boolean isCurrentPassBakeable();
    void setCurrentPassBakeable(boolean bakeable);

    BufferBuilder getCurrentPassNestedBuilder();
    void setCurrentPassNestedBuilder(BufferBuilder nestedBuilder);

    MatrixStack getCurrentPassOriginalStack();
    void setCurrentPassOriginalStack(MatrixStack originalStack);

    default void updateCurrentPass(MatrixStack matrices, VertexConsumer vertexConsumer) {
        setCurrentPassNestedBuilder(GlobalModelUtils.getNestedBufferBuilder(vertexConsumer));
        setCurrentPassBakeable(GlobalModelUtils.isSmartBufferBuilder(getCurrentPassNestedBuilder()));
        setCurrentPassOriginalStack(matrices);
    }

    default MatrixStack tryReplaceMatrixStack(MatrixStack existingStack) {
        if (isCurrentPassBakeable()) {
            return GlobalModelUtils.BAKING_MATRIX_STACK;
        } else {
            return existingStack;
        }
    }

    default VertexConsumer tryDisableImmediateRendering(VertexConsumer existingConsumer) {
        if (getBakedVertices() != null && isCurrentPassBakeable()) {
            return null;
        } else {
            return existingConsumer;
        }
    }

    default void tryCreateVbo() {
        if (getBakedVertices() == null && isCurrentPassBakeable()) {
            if (MinecraftClient.getInstance().getWindow() != null) {
                getCurrentPassNestedBuilder().end();
                setBakedVertices(new VertexBuffer());
                getBakedVertices().upload(getCurrentPassNestedBuilder());
            }
        }
    }

    default void tryRenderVbo(int light, int overlay, float red, float green, float blue, float alpha) {
        if (getBakedVertices() != null && isCurrentPassBakeable()) {
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("Color").set(red, green, blue, alpha);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV1").set(overlay & 65535, overlay >> 16 & 65535);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV2").set(light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295), light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295));

            // TODO OPT: Use buffers larger than ssboSize if available to avoid unnecessary creation
            SectionedPbo pbo = GlobalModelUtils.SIZE_TO_GL_BUFFER_POINTER.computeIfAbsent(GlobalModelUtils.currentMatrices.size() * GlobalModelUtils.STRUCT_SIZE, ssboSize -> {
                int name = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, name);
                int fullSize = ssboSize * GlobalModelUtils.BUFFER_SECTIONS;
                // TODO: nglNamedBufferStorage or nglNamedBufferStorageEXT
                ARBBufferStorage.nglBufferStorage(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, fullSize, MemoryUtil.NULL, GlobalModelUtils.BUFFER_CREATION_FLAGS);
                return new SectionedPbo(
                        GL30C.glMapBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 0, fullSize, GlobalModelUtils.BUFFER_MAP_FLAGS),
                        name,
                        GlobalModelUtils.BUFFER_SECTIONS,
                        ssboSize
                );
            });

            if (pbo.shouldBindBuffer()) {
                GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, pbo.getName());
            }

            long currentSyncObject = pbo.getCurrentSyncObject();

            if (currentSyncObject != MemoryUtil.NULL) {
                int waitReturn = GL32C.GL_UNSIGNALED;
                while (waitReturn != GL32C.GL_ALREADY_SIGNALED && waitReturn != GL32C.GL_CONDITION_SATISFIED) {
                    waitReturn = GL32C.glClientWaitSync(currentSyncObject, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 1);
                }
            }

            int sectionStartPos = pbo.getCurrentSection() * pbo.getSectionSize();

            for (int i = 0; i < GlobalModelUtils.currentMatrices.size(); i++) {
                Matrix4f modelEntry = GlobalModelUtils.currentMatrices.get(i);
                pbo.getPointer().position(i * GlobalModelUtils.STRUCT_SIZE + sectionStartPos);
                if (modelEntry != null) {
                    pbo.getPointer().putFloat(modelEntry.a00).putFloat(modelEntry.a10).putFloat(modelEntry.a20).putFloat(modelEntry.a30)
                            .putFloat(modelEntry.a01).putFloat(modelEntry.a11).putFloat(modelEntry.a21).putFloat(modelEntry.a31)
                            .putFloat(modelEntry.a02).putFloat(modelEntry.a12).putFloat(modelEntry.a22).putFloat(modelEntry.a32)
                            .putFloat(modelEntry.a03).putFloat(modelEntry.a13).putFloat(modelEntry.a23).putFloat(modelEntry.a33);
                } else {
                    pbo.getPointer().put(GlobalModelUtils.IDENTITY_MATRIX_BUFFER);
                }
            }

            GlobalModelUtils.currentMatrices.clear();

            // TODO: glFlushMappedNamedBufferRange
            GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, sectionStartPos, pbo.getSectionSize());

            if (currentSyncObject != MemoryUtil.NULL) {
                GL32C.glDeleteSync(currentSyncObject);
            }
            pbo.setCurrentSyncObject(GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));

            GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 1, pbo.getName(), sectionStartPos, pbo.getSectionSize());

            pbo.nextSection();

            RenderLayer layer = ((RenderLayerCreatedBufferBuilder) getCurrentPassNestedBuilder()).getRenderLayer();
            if (layer == null) {
                throw new RuntimeException("RenderLayer provided with BufferBuilder is null");
            }

            layer.startDrawing();
            getBakedVertices().setShader(getCurrentPassOriginalStack().peek().getModel(), RenderSystem.getProjectionMatrix(), BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL);
            layer.endDrawing();
        }
    }

}
