package com.oroarmor.bakedminecraftmodels.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.data.ModelRenderSubtypeData;
import com.oroarmor.bakedminecraftmodels.data.ModelTypeData;
import com.oroarmor.bakedminecraftmodels.mixin.ShaderAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.VertexBufferAccessor;
import com.oroarmor.bakedminecraftmodels.model.InstancedRenderDispatcher;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedSyncObjects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Shader;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.Window;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.util.List;

import static com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils.*;
import static com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils.bakingData;

public class GlBufferTexRenderDispacher implements InstancedRenderDispatcher {

    private static final int BUFFER_CREATION_FLAGS = GL30C.GL_MAP_WRITE_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    private static final int BUFFER_MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_FLUSH_EXPLICIT_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    private static final int BUFFER_SECTIONS = 3;

    private static final int ENTITY_LIMIT = 8192;

    private static final long PART_PBO_SIZE = ENTITY_LIMIT * 16 * PART_STRUCT_SIZE; // assume each entity has on average 16 parts
    private static final long MODEL_PBO_SIZE = ENTITY_LIMIT * MODEL_STRUCT_SIZE;

    private static final String MODEL_BUFFER_TEX_NAME = "ModelBuffer";
    private static final String PART_BUFFER_TEX_NAME = "PartBuffer";

    private static final SectionedSyncObjects SYNC_OBJECTS = new SectionedSyncObjects(BUFFER_SECTIONS);
    private static SectionedPersistentBuffer PART_PBO;
    private static SectionedPersistentBuffer MODEL_PBO;
    private static int MODEL_BUFFER_TEX_ID;
    private static int PART_BUFFER_TEX_ID;


    private static SectionedPersistentBuffer getOrCreatePartPbo() {
        if (PART_PBO == null) {
            PART_PBO = createTexPersistentBuffer(PART_PBO_SIZE);
        }
        return PART_PBO;
    }

    private static SectionedPersistentBuffer getOrCreateModelPbo() {
        if (MODEL_PBO == null) {
            MODEL_PBO = createTexPersistentBuffer(MODEL_PBO_SIZE);
        }
        return MODEL_PBO;
    }

    private static SectionedPersistentBuffer createTexPersistentBuffer(long ssboSize) {
        int name = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL31C.GL_TEXTURE_BUFFER, name);
        long fullSize = ssboSize * BUFFER_SECTIONS;
        ARBBufferStorage.nglBufferStorage(GL31C.GL_TEXTURE_BUFFER, fullSize, MemoryUtil.NULL, BUFFER_CREATION_FLAGS);
        return new SectionedPersistentBuffer(
                GL30C.nglMapBufferRange(GL31C.GL_TEXTURE_BUFFER, 0, fullSize, BUFFER_MAP_FLAGS),
                name,
                BUFFER_SECTIONS,
                ssboSize
        );
    }

    private static int getOrCreatePartBufferTex() {
        if (PART_BUFFER_TEX_ID == 0) {
            PART_BUFFER_TEX_ID = GlStateManager._genTexture();
            GL11.glBindTexture(GL31C.GL_TEXTURE_BUFFER, PART_BUFFER_TEX_ID);
            GL31C.glTexBuffer(GL31C.GL_TEXTURE_BUFFER, GL30C.GL_RGBA32F, getOrCreatePartPbo().getName());
        }
        return PART_BUFFER_TEX_ID;
    }

    private static int getOrCreateModelBufferTex() {
        if (MODEL_BUFFER_TEX_ID == 0) {
            MODEL_BUFFER_TEX_ID = GlStateManager._genTexture();
            GL11.glBindTexture(GL31C.GL_TEXTURE_BUFFER, MODEL_BUFFER_TEX_ID);
            GL31C.glTexBuffer(GL31C.GL_TEXTURE_BUFFER, GL30C.GL_RGBA32F, getOrCreateModelPbo().getName());
        }
        return MODEL_BUFFER_TEX_ID;
    }

    public void renderQueues() {
        int instanceOffset = 0;

        SectionedPersistentBuffer partPbo = getOrCreatePartPbo();
        SectionedPersistentBuffer modelPbo = getOrCreateModelPbo();
        int partBufferTexId = getOrCreatePartBufferTex();
        int modelBufferTexId = getOrCreateModelBufferTex();

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

        GlStateManager._glBindBuffer(GL31C.GL_TEXTURE_BUFFER, partPbo.getName());
        GL30C.glFlushMappedBufferRange(GL31C.GL_TEXTURE_BUFFER, partSectionStartPos, partPbo.getPositionOffset());

        GlStateManager._glBindBuffer(GL31C.GL_TEXTURE_BUFFER, modelPbo.getName());
        GL30C.glFlushMappedBufferRange(GL31C.GL_TEXTURE_BUFFER, modelSectionStartPos, modelPbo.getPositionOffset());

        if (currentPartSyncObject != MemoryUtil.NULL) {
            GL32C.glDeleteSync(currentPartSyncObject);
        }
        SYNC_OBJECTS.setCurrentSyncObject(GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));

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

                String[] activeTextureBuffers = new String[12];

                ShaderAccessor shaderAccessor = (ShaderAccessor) shader;
                for (int i = 0; i < 12; ++i) {
                    List<String> samplerNames = shaderAccessor.getSamplerNames();
                    String samplerName = i >= samplerNames.size() ? null : samplerNames.get(i);
                    if (samplerName == null || (!samplerName.equals(MODEL_BUFFER_TEX_NAME) && !samplerName.equals(PART_BUFFER_TEX_NAME))) {
                        int j = RenderSystem.getShaderTexture(i);
                        shader.addSampler("Sampler" + i, j);
                    } else {
                        activeTextureBuffers[i] = samplerName;
                    }
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
                    BakedMinecraftModelsShaderManager.INSTANCE_OFFSET.set((int) (instanceOffset + (modelPbo.getSectionOffset() / MODEL_STRUCT_SIZE)));
                }

                RenderSystem.setupShaderLights(shader);
                vertexBufferAccessor.invokeBindVertexArray();
                vertexBufferAccessor.invokeBind();
                vertexBuffer.getElementFormat().startDrawing();
                shader.bind();

                int previousActiveTex = GlStateManager._getActiveTexture();
                for (int i = 0; i < activeTextureBuffers.length; i++) {
                    String samplerName = activeTextureBuffers[i];
                    int texId = samplerName == null ? -1 : switch (samplerName) {
                        case MODEL_BUFFER_TEX_NAME -> modelBufferTexId;
                        case PART_BUFFER_TEX_NAME -> partBufferTexId;
                        default -> -1;
                    };

                    if (texId != -1) {
                        int k = GlUniform.getUniformLocation(shader.getProgramRef(), samplerName);
                        GlUniform.uniform1(k, i);
                        RenderSystem.activeTexture(GL13C.GL_TEXTURE0 + i);
                        RenderSystem.enableTexture();
                        GL11.glBindTexture(GL31C.GL_TEXTURE_BUFFER, texId);
                    }
                }
                GlStateManager._activeTexture(previousActiveTex);

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