/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import graphics.kiln.bakedminecraftmodels.BakedMinecraftModels;
import graphics.kiln.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import graphics.kiln.bakedminecraftmodels.debug.DebugInfo;
import graphics.kiln.bakedminecraftmodels.mixin.buffer.VertexBufferAccessor;
import graphics.kiln.bakedminecraftmodels.model.InstancedRenderDispatcher;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedSyncObjects;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Shader;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class GlSsboRenderDispacher implements InstancedRenderDispatcher {

    public static final int BUFFER_CREATION_FLAGS = GL30C.GL_MAP_WRITE_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
    public static final int BUFFER_MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_FLUSH_EXPLICIT_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
    public static final int BUFFER_SECTIONS = 3;
    public static final long PART_PBO_SIZE = 5242880L; // 5 MiB
    public static final long MODEL_PBO_SIZE = 524288L; // 500 KiB

    private static SectionedPersistentBuffer PART_PBO;
    private static SectionedPersistentBuffer MODEL_PBO;
    private static SectionedSyncObjects SYNC_OBJECTS = new SectionedSyncObjects(BUFFER_SECTIONS);

    private static SectionedPersistentBuffer getOrCreatePartPbo() {
        if (PART_PBO == null) {
            PART_PBO = createSsboPersistentBuffer(PART_PBO_SIZE);
        }
        return PART_PBO;
    }

    private static SectionedPersistentBuffer getOrCreateModelPbo() {
        if (MODEL_PBO == null) {
            MODEL_PBO = createSsboPersistentBuffer(MODEL_PBO_SIZE);
        }
        return MODEL_PBO;
    }

    private static SectionedPersistentBuffer createSsboPersistentBuffer(long ssboSize) {
        int name = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, name);
        long fullSize = ssboSize * BUFFER_SECTIONS;
        ARBBufferStorage.nglBufferStorage(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, fullSize, MemoryUtil.NULL, BUFFER_CREATION_FLAGS);
        return new SectionedPersistentBuffer(
                GL30C.nglMapBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 0, fullSize, BUFFER_MAP_FLAGS),
                name,
                BUFFER_SECTIONS,
                ssboSize
        );
    }
    
    public void renderQueues() {
        if (!GlobalModelUtils.bakingData.isEmptyShallow()) {
            SectionedPersistentBuffer partPbo = getOrCreatePartPbo();
            SectionedPersistentBuffer modelPbo = getOrCreateModelPbo();

            long currentPartSyncObject = SYNC_OBJECTS.getCurrentSyncObject();

            if (currentPartSyncObject != MemoryUtil.NULL) {
                int waitResult = GL32C.glClientWaitSync(currentPartSyncObject, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 10000000); // 10 seconds
                if (waitResult == GL32C.GL_WAIT_FAILED || waitResult == GL32C.GL_TIMEOUT_EXPIRED) {
                    BakedMinecraftModels.LOGGER.error("OpenGL sync failed");
                }
            }

            long partSectionStartPos = partPbo.getCurrentSection() * partPbo.getSectionSize();
            long modelSectionStartPos = modelPbo.getCurrentSection() * modelPbo.getSectionSize();

            GlobalModelUtils.bakingData.writeToBuffer(modelPbo, partPbo);

            GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, partPbo.getName());
            GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, partSectionStartPos, partPbo.getPositionOffset());

            GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, modelPbo.getName());
            GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, modelSectionStartPos, modelPbo.getPositionOffset());

            if (currentPartSyncObject != MemoryUtil.NULL) {
                GL32C.glDeleteSync(currentPartSyncObject);
            }
            SYNC_OBJECTS.setCurrentSyncObject(GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));

            GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 1, partPbo.getName(), partSectionStartPos, partPbo.getPositionOffset());
            GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 2, modelPbo.getName(), modelSectionStartPos, modelPbo.getPositionOffset());

            DebugInfo.currentPartBufferSize = partPbo.getPositionOffset();
            DebugInfo.currentModelBufferSize = modelPbo.getPositionOffset();
            partPbo.nextSection();
            modelPbo.nextSection();
            SYNC_OBJECTS.nextSection();

            int instanceOffset = 0;

            RenderLayer currentRenderLayer = null;
            VertexBuffer currentVertexBuffer = null;
            BufferRenderer.unbindAll();

            for (Map<RenderLayer, Map<VboBackedModel, List<?>>> perOrderedSectionData : GlobalModelUtils.bakingData.getInternalData()) {

                for (Map.Entry<RenderLayer, Map<VboBackedModel, List<?>>> perRenderLayerData : perOrderedSectionData.entrySet()) {
                    RenderLayer nextRenderLayer = perRenderLayerData.getKey();
                    if (currentRenderLayer == null) {
                        currentRenderLayer = nextRenderLayer;
                        currentRenderLayer.startDrawing();
                    } else if (!currentRenderLayer.equals(nextRenderLayer)) {
                        currentRenderLayer.endDrawing();
                        currentRenderLayer = nextRenderLayer;
                        currentRenderLayer.startDrawing();
                    }

                    for (Map.Entry<VboBackedModel, List<?>> perModelData : perRenderLayerData.getValue().entrySet()) {
                        VertexBuffer nextVertexBuffer = perModelData.getKey().getBakedVertices();
                        VertexBufferAccessor vertexBufferAccessor = (VertexBufferAccessor) nextVertexBuffer;
                        int vertexCount = vertexBufferAccessor.getVertexCount();
                        if (vertexCount <= 0) continue;

                        if (!Objects.equals(currentVertexBuffer, nextVertexBuffer)) {
                            currentVertexBuffer = nextVertexBuffer;
                            vertexBufferAccessor.invokeBindVertexArray();
                            vertexBufferAccessor.invokeBind();
                        }

                        VertexFormat.DrawMode drawMode = vertexBufferAccessor.getDrawMode();

                        int instanceCount = perModelData.getValue().size();
                        if (instanceCount <= 0) continue;

                        Shader shader = RenderSystem.getShader();

                        for (int i = 0; i < 12; ++i) {
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
                            shader.screenSize.set((float) window.getFramebufferWidth(), (float) window.getFramebufferHeight());
                        }

                        if (shader.lineWidth != null && (drawMode == VertexFormat.DrawMode.LINES || drawMode == VertexFormat.DrawMode.LINE_STRIP)) {
                            shader.lineWidth.set(RenderSystem.getShaderLineWidth());
                        }

                        if (BakedMinecraftModelsShaderManager.INSTANCE_OFFSET_1 != null) {
                            BakedMinecraftModelsShaderManager.INSTANCE_OFFSET_1.set(instanceOffset);
                        }

                        RenderSystem.setupShaderLights(shader);
                        nextVertexBuffer.getElementFormat().startDrawing();
                        shader.bind();
                        GL31C.glDrawElementsInstanced(drawMode.mode, vertexCount, vertexBufferAccessor.getVertexFormat().count, MemoryUtil.NULL, instanceCount);
                        shader.unbind();
                        nextVertexBuffer.getElementFormat().endDrawing();

                        instanceOffset += instanceCount;

                        DebugInfo.ModelDebugInfo currentDebugInfo = DebugInfo.modelToDebugInfoMap.computeIfAbsent(perModelData.getKey().getClass().getSimpleName(), (ignored) -> new DebugInfo.ModelDebugInfo());
                        currentDebugInfo.instances += instanceCount;
                        currentDebugInfo.sets++;
                    }
                }
            }

            if (currentRenderLayer != null) {
                currentRenderLayer.endDrawing();
            }

            GlobalModelUtils.bakingData.reset();
        }
    }
    
}
