/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.gl;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import graphics.kiln.bakedminecraftmodels.BakedMinecraftModels;
import graphics.kiln.bakedminecraftmodels.data.BakingData;
import graphics.kiln.bakedminecraftmodels.debug.DebugInfo;
import graphics.kiln.bakedminecraftmodels.mixin.buffer.VertexBufferAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.model.InstancedRenderDispatcher;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedSyncObjects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.Window;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

public class GlSsboRenderDispacher implements InstancedRenderDispatcher {

    public static final int BUFFER_CREATION_FLAGS = GL30C.GL_MAP_WRITE_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
    public static final int BUFFER_MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_FLUSH_EXPLICIT_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;
    public static final int BUFFER_SECTIONS = 3;
    public static final long PART_PBO_SIZE = 9175040L; // 8.75 MiB
    public static final long MODEL_PBO_SIZE = 524288L; // 500 KiB
    public static final long TRANSPARENT_EBO_SIZE = 512 * 1024; // 512 KiB - TODO figure out what a reasonable value is

    public final SectionedPersistentBuffer partPbo;
    public final SectionedPersistentBuffer modelPbo;
    public final SectionedSyncObjects syncObjects;
    public final SectionedPersistentBuffer transparencyEbo; // FIXME not a PBO

    public GlSsboRenderDispacher() {
        partPbo = createSsboPersistentBuffer(PART_PBO_SIZE);
        modelPbo = createSsboPersistentBuffer(MODEL_PBO_SIZE);
        syncObjects = new SectionedSyncObjects(BUFFER_SECTIONS);

        // FIXME triple-buffer
        int name = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, name);
        long fullSize = TRANSPARENT_EBO_SIZE * BUFFER_SECTIONS;
        ARBBufferStorage.nglBufferStorage(GL15.GL_ELEMENT_ARRAY_BUFFER, fullSize, MemoryUtil.NULL, BUFFER_CREATION_FLAGS);
        transparencyEbo = new SectionedPersistentBuffer(
                GL30C.nglMapBufferRange(GL15.GL_ELEMENT_ARRAY_BUFFER, 0, fullSize, BUFFER_MAP_FLAGS),
                name,
                BUFFER_SECTIONS,
                TRANSPARENT_EBO_SIZE
        );
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

            long currentPartSyncObject = syncObjects.getCurrentSyncObject();

            if (currentPartSyncObject != MemoryUtil.NULL) {
                int waitResult = GL32C.glClientWaitSync(currentPartSyncObject, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 10000000); // 10 seconds
                if (waitResult == GL32C.GL_WAIT_FAILED || waitResult == GL32C.GL_TIMEOUT_EXPIRED) {
                    BakedMinecraftModels.LOGGER.error("OpenGL sync failed");
                }
            }

            GlobalModelUtils.bakingData.writeData();

            long partSectionStartPos = partPbo.getCurrentSection() * partPbo.getSectionSize();
            long modelSectionStartPos = modelPbo.getCurrentSection() * modelPbo.getSectionSize();
            long partLength = partPbo.getPositionOffset().getAcquire();
            long modelLength = modelPbo.getPositionOffset().getAcquire();

            GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, partPbo.getName());
            GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, partSectionStartPos, partLength);

            GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, modelPbo.getName());
            GL30C.glFlushMappedBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, modelSectionStartPos, modelLength);

            if (currentPartSyncObject != MemoryUtil.NULL) {
                GL32C.glDeleteSync(currentPartSyncObject);
            }
            syncObjects.setCurrentSyncObject(GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0));

            GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 1, partPbo.getName(), partSectionStartPos, partLength);
            GL30C.glBindBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 2, modelPbo.getName(), modelSectionStartPos, modelLength);

            DebugInfo.currentPartBufferSize = partLength;
            DebugInfo.currentModelBufferSize = modelLength;
            partPbo.nextSection();
            modelPbo.nextSection();
            syncObjects.nextSection();

            int instanceOffset = 0;

            RenderLayer currentRenderLayer = null;
            VertexBuffer currentVertexBuffer = null;
            BufferRenderer.unbindAll();

            for (Map<RenderLayer, Map<VboBackedModel, List<BakingData.PerInstanceData>>> perOrderedSectionData : GlobalModelUtils.bakingData) {

                for (Map.Entry<RenderLayer, Map<VboBackedModel, List<BakingData.PerInstanceData>>> perRenderLayerData : perOrderedSectionData.entrySet()) {
                    RenderLayer nextRenderLayer = perRenderLayerData.getKey();
                    if (currentRenderLayer == null) {
                        currentRenderLayer = nextRenderLayer;
                        currentRenderLayer.startDrawing();
                    } else if (!currentRenderLayer.equals(nextRenderLayer)) {
                        currentRenderLayer.endDrawing();
                        currentRenderLayer = nextRenderLayer;
                        currentRenderLayer.startDrawing();
                    }

                    Shader shader = RenderSystem.getShader();

                    //noinspection ConstantConditions
                    RenderPhase.Transparency transparency = ((MultiPhaseParametersAccessor) (Object)
                            (((MultiPhaseRenderPassAccessor) nextRenderLayer).getPhases())).getTransparency();

                    for (Map.Entry<VboBackedModel, List<BakingData.PerInstanceData>> perModelData : perRenderLayerData.getValue().entrySet()) {
                        VertexBuffer nextVertexBuffer = perModelData.getKey().getBakedVertices();
                        VertexBufferAccessor vertexBufferAccessor = (VertexBufferAccessor) nextVertexBuffer;
                        int vertexCount = vertexBufferAccessor.getVertexCount();
                        if (vertexCount <= 0) continue;

                        if (currentVertexBuffer == null) {
                            currentVertexBuffer = nextVertexBuffer;
                            vertexBufferAccessor.invokeBindVertexArray();
                            vertexBufferAccessor.invokeBind();
                            // TODO only bind for transparencies?
                            GL30C.glBindBufferBase(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 3, vertexBufferAccessor.getVertexBufferId());
                            currentVertexBuffer.getElementFormat().startDrawing();
                        } else if (!currentVertexBuffer.equals(nextVertexBuffer)) {
                            currentVertexBuffer.getElementFormat().endDrawing();
                            currentVertexBuffer = nextVertexBuffer;
                            vertexBufferAccessor.invokeBindVertexArray();
                            vertexBufferAccessor.invokeBind();
                            // TODO only bind for transparencies?
                            GL30C.glBindBufferBase(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 3, vertexBufferAccessor.getVertexBufferId());
                            currentVertexBuffer.getElementFormat().startDrawing();
                        }

                        VertexFormat.DrawMode drawMode = vertexBufferAccessor.getDrawMode();

                        int instanceCount = perModelData.getValue().size();
                        if (instanceCount <= 0) continue;

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

                        // we have to manually get it from the shader every time because different shaders have different uniform objects for the same uniform.
                        GlUniform instanceOffsetUniform = shader.getUniform("InstanceOffset");
                        if (instanceOffsetUniform != null) {
                            instanceOffsetUniform.set(instanceOffset);
                        }

                        if (isTransparencySorted(transparency)) {
                            drawSortedFakeInstanced(nextRenderLayer, shader, vertexBufferAccessor, perModelData);
                        } else {
                            RenderSystem.setupShaderLights(shader);
                            shader.bind();
                            GL31C.glDrawElementsInstanced(drawMode.mode, vertexCount, vertexBufferAccessor.getVertexFormat().count, MemoryUtil.NULL, instanceCount);
                            shader.unbind();
                        }

                        instanceOffset += instanceCount;

                        DebugInfo.ModelDebugInfo currentDebugInfo = DebugInfo.modelToDebugInfoMap.computeIfAbsent(perModelData.getKey().getClass().getSimpleName(), (ignored) -> new DebugInfo.ModelDebugInfo());
                        currentDebugInfo.instances += instanceCount;
                        currentDebugInfo.sets++;
                    }
                }
            }

            if (currentVertexBuffer != null) {
                currentVertexBuffer.getElementFormat().endDrawing();
            }

            if (currentRenderLayer != null) {
                currentRenderLayer.endDrawing();
            }

            GlobalModelUtils.bakingData.reset();
        }
    }

    /**
     * Do a 'fake' glDrawElementsInstanced call, with vertex sorting.
     * <p>
     * This builds a EBO containing all the vertices for all the elements to draw. The notable
     * thing here is that we have control of the draw order - we can sort the elements by depth
     * from the camera, and use this to batch the rendering of transparent objects.
     */
    private void drawSortedFakeInstanced(RenderLayer renderLayer, Shader shader, VertexBufferAccessor vba,
                                         Map.Entry<VboBackedModel, List<BakingData.PerInstanceData>> perModelData) {
        int instanceCount = perModelData.getValue().size();
        int indexCount = vba.getVertexCount();
        VertexFormat.DrawMode drawMode = vba.getDrawMode();

        int eboLen = 0;
        int eboOffset = 0; // TODO triple-buffer

        VertexBuffer vb = (VertexBuffer) vba;

        int[] template = new int[]{
                0,
                1,
                2,
                2,
                3,
                0,
        };

        long ptr = transparencyEbo.getSectionedPointer();
        MemoryUtil.memSet(ptr, 0, 500);
        for (int i = 0; i < instanceCount; i++) {
            BakingData.PerInstanceData instance = perModelData.getValue().get(i);
            ByteBuffer instanceEbo = instance.eboData();
            instanceEbo.position(0);

            for (int j = 0; j < indexCount; j++) {
                // Temporary hack for testing - TODO assert instanceEbo len == indexCount
                // TODO figure out format conversions properly
                int innerOffset = switch (instance.eboType()) {
                    case INT -> instanceEbo.getInt();
                    case SHORT -> instanceEbo.getShort();
                    case BYTE -> instanceEbo.get();
                };

                MemoryUtil.memPutInt(ptr + eboLen, innerOffset + indexCount * i);
                eboLen += 4;
            }
        }

        GlUniform countUniform = shader.getUniform("InstanceVertCount"); // TODO rename
        if (countUniform != null) {
            countUniform.set(indexCount);
        }

        // FIXME triple-buffer the EBO
        // TODO move this to the top of renderQueue with the SSBOs
        GlStateManager._glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, transparencyEbo.getName());
        GL30C.glFlushMappedBufferRange(GL15.GL_ELEMENT_ARRAY_BUFFER, eboOffset, eboLen);

        // TODO do we need to disable the VAO here? It's not bound in the shader, can it still
        //  cause issues?

        RenderSystem.setupShaderLights(shader);
        shader.bind();
        // type was vba.getVertexFormat().count
        GL31C.glDrawElements(drawMode.mode, indexCount * instanceCount, GL15.GL_UNSIGNED_INT, eboOffset);
        shader.unbind();

        // TODO Unbind EBO?
    }

    public static boolean isTransparencySorted(RenderPhase.Transparency transparency) {
        String name = transparency.toString();
        return !name.equals("no_transparency") && !name.equals("additive_transparency");
    }
}
