package com.oroarmor.bakedminecraftmodels.mixin.model;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsRenderLayerManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;
import com.oroarmor.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils;
import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.AnimalModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnimalModel.class)
public abstract class AnimalModelMixin {

    @Unique
    @Nullable
    private VertexBuffer bmm$bakedVertices;

    @Unique
    private boolean currentPassBakeable;

    @Unique
    private BufferBuilder currentPassNestedBuilder;

    @Unique
    private MatrixStack currentPassOriginalStack;

    @Shadow
    public abstract void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha);

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private void getCurrentPassStatus(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        currentPassNestedBuilder = GlobalModelUtils.getNestedBufferBuilder(vertices);
        currentPassBakeable = GlobalModelUtils.isSmartBufferBuilder(currentPassNestedBuilder);
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"))
    private MatrixStack setMatrixStack(MatrixStack existingStack) {
        currentPassOriginalStack = existingStack;
        if (currentPassBakeable) {
            return GlobalModelUtils.BAKING_MATRIX_STACK;
        } else {
            return existingStack;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"), cancellable = true)
    private void renderWithVbo(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (bmm$bakedVertices == null && !currentPassNestedBuilder.isBuilding()
                && ((MultiPhaseParametersAccessor) (Object) ((MultiPhaseRenderPassAccessor) ((RenderLayerCreatedBufferBuilder) currentPassNestedBuilder)
                .getRenderLayer())
                .getPhases())
                .getShader()
                .equals(BakedMinecraftModelsRenderLayerManager.SMART_ENTITY_CUTOUT_NO_CULL_PHASE)) {
            currentPassNestedBuilder.begin(VertexFormat.DrawMode.QUADS, BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
            throw new RuntimeException("this should never hit");
        }

        if (bmm$bakedVertices != null && currentPassBakeable) {
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("Color").set(red, green, blue, alpha);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV1").set(overlay & 65535, overlay >> 16 & 65535);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV2").set(light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295), light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295));

            // TODO OPT: Use buffers larger than ssboSize if available to avoid unnecessary creation
            SectionedPbo pbo = GlobalModelUtils.SIZE_TO_GL_BUFFER_POINTER.computeIfAbsent(GlobalModelUtils.currentMatrices.size() * BakedMinecraftModels.STRUCT_SIZE, ssboSize -> {
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
                pbo.getPointer().position(i * BakedMinecraftModels.STRUCT_SIZE + sectionStartPos);
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

            RenderLayer layer = ((RenderLayerCreatedBufferBuilder) currentPassNestedBuilder).getRenderLayer();
            if (layer == null) {
                throw new RuntimeException("RenderLayer provided with BufferBuilder is null");
            }

            layer.startDrawing();
            bmm$bakedVertices.setShader(currentPassOriginalStack.peek().getModel(), RenderSystem.getProjectionMatrix(), BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL);
            layer.endDrawing();
            ci.cancel();
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    private void tryCreateVbo(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (bmm$bakedVertices == null && currentPassBakeable) {
            if (MinecraftClient.getInstance().getWindow() != null) {
                currentPassNestedBuilder.end();
                bmm$bakedVertices = new VertexBuffer();
                bmm$bakedVertices.upload(currentPassNestedBuilder);
                this.render(matrices, vertexConsumer, light, overlay, red, green, blue, alpha);
            }
        }
    }
}
