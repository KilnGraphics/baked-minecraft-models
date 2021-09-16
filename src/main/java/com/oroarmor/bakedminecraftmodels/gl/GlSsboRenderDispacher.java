package com.oroarmor.bakedminecraftmodels.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.data.ModelRenderSubtypeData;
import com.oroarmor.bakedminecraftmodels.data.ModelTypeData;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.VertexBufferAccessor;
import com.oroarmor.bakedminecraftmodels.model.InstancedRenderDispatcher;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedSyncObjects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Shader;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import static com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils.*;

public class GlSsboRenderDispacher implements InstancedRenderDispatcher {

    private static final int BUFFER_CREATION_FLAGS = GL30C.GL_MAP_WRITE_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
    private static final int BUFFER_MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_FLUSH_EXPLICIT_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
    private static final int BUFFER_SECTIONS = 3;
    private static final int ENTITY_LIMIT = 8192;
    public static final long PART_PBO_SIZE = ENTITY_LIMIT * 16 * PART_STRUCT_SIZE; // assume each entity has on average 16 parts
    public static final long MODEL_PBO_SIZE = ENTITY_LIMIT * MODEL_STRUCT_SIZE;

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
        int instanceOffset = 0;

        SectionedPersistentBuffer partPbo = getOrCreatePartPbo();
        SectionedPersistentBuffer modelPbo = getOrCreateModelPbo();

        long currentPartSyncObject = SYNC_OBJECTS.getCurrentSyncObject();

        if (currentPartSyncObject != MemoryUtil.NULL) {
            int waitReturn = GL32C.GL_UNSIGNALED;
            while (waitReturn != GL32C.GL_ALREADY_SIGNALED && waitReturn != GL32C.GL_CONDITION_SATISFIED) {
                waitReturn = GL32C.glClientWaitSync(currentPartSyncObject, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 1);
            }
        }

        long partSectionStartPos = partPbo.getCurrentSection() * partPbo.getSectionSize();
        long modelSectionStartPos = modelPbo.getCurrentSection() * modelPbo.getSectionSize();

        bakingData.writeToBuffer(modelPbo, partPbo);

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

        partPbo.nextSection();
        modelPbo.nextSection();
        SYNC_OBJECTS.nextSection();

        for (ModelTypeData modelTypeData : bakingData.getAllModelTypeData()) {

            VertexBuffer vertexBuffer = modelTypeData.getModel().getBakedVertices();
            VertexBufferAccessor vertexBufferAccessor = (VertexBufferAccessor) vertexBuffer;
            int vertexCount = vertexBufferAccessor.getVertexCount();
            if (vertexCount <= 0) continue;

            VertexFormat.DrawMode drawMode = vertexBufferAccessor.getDrawMode();

            for (ModelRenderSubtypeData subtypeData : modelTypeData.getAllSubtypeData()) {
                RenderLayer layer = subtypeData.getRenderLayer();
                int instanceCount = subtypeData.getInstanceCount();
                if (instanceCount <= 0) continue;

                layer.startDrawing();
                Shader shader = RenderSystem.getShader();
                BufferRenderer.unbindAll();

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

                if (BakedMinecraftModelsShaderManager.INSTANCE_OFFSET != null) {
                    BakedMinecraftModelsShaderManager.INSTANCE_OFFSET.set(instanceOffset);
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

                instanceOffset += instanceCount;
            }
        }

        bakingData.reset();
    }
    
}
