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

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;
import com.oroarmor.bakedminecraftmodels.access.ModelID;
import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import com.oroarmor.bakedminecraftmodels.ssbo.SsboInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements ModelID {
    @Unique
    private int bmm$id;

    @Shadow
    @Final
    private List<ModelPart.Cuboid> cuboids;

    @Shadow
    public abstract void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha);

    @Shadow
    public abstract void rotate(MatrixStack matrix);

    @Shadow
    @Final
    private Map<String, ModelPart> children;

    @Shadow public boolean visible;

    @Override
    public void setId(int id) {
        bmm$id = id;
        cuboids.forEach(cuboid -> ((ModelID) cuboid).setId(bmm$id));
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @Inject(method = "rotate", at = @At("HEAD"), cancellable = true)
    public void setSsboRotation(MatrixStack matrix, CallbackInfo ci) {
        if (bmm$usingSmartRenderer && !bmm$buildingMatrices) {
            ci.cancel();
        }
    }

    @Unique
    private static Object bmm$initialModelPartForBaking = null;

    @Unique
    private static boolean bmm$usingSmartRenderer = false;

    @Unique
    private static boolean bmm$buildingMatrices;

    @Unique
    @Nullable
    protected VertexBuffer bmm$bakedVertices;

    @Unique
    private static final Int2ObjectMap<SsboInfo> bmm$SIZE_TO_GL_BUFFER_POINTER = new Int2ObjectOpenHashMap<>();

    @Unique
    private static final int BUFFER_CREATION_FLAGS = GL30C.GL_MAP_WRITE_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    @Unique
    private static final int BUFFER_MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_FLUSH_EXPLICIT_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    @Unique
    private static long syncObj = MemoryUtil.NULL;

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("HEAD"), cancellable = true)
    public void useVertexBufferRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        BufferBuilder nestedBufferBuilder = BakedMinecraftModels.getNestedBufferBuilder(vertices);

        bmm$usingSmartRenderer = ((BufferBuilderAccessor) nestedBufferBuilder).getFormat() == BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT;

        if (bmm$initialModelPartForBaking == null && bmm$bakedVertices == null) {
            bmm$initialModelPartForBaking = this;
            if (!nestedBufferBuilder.isBuilding() && bmm$usingSmartRenderer) {
                nestedBufferBuilder.begin(VertexFormat.DrawMode.QUADS, BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
            }
        }

        if (bmm$bakedVertices != null && bmm$usingSmartRenderer) {
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("Color").set(red, green, blue, alpha);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV1").set(overlay & 65535, overlay >> 16 & 65535);
            BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL.getUniform("UV2").set(light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295), light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 65295));

            matrices.push();
            bmm$buildingMatrices = true;
            ObjectArrayList<MatrixStack.Entry> entries = new ObjectArrayList<>();
            this.bmm$createMatrixTransformations(matrices, entries);
            bmm$buildingMatrices = false;
            matrices.pop();

            int ssboSize = entries.size() * BakedMinecraftModels.STRUCT_SIZE;

            SsboInfo ssbo = bmm$SIZE_TO_GL_BUFFER_POINTER.computeIfAbsent(ssboSize, _size -> {
                int name = GlStateManager._glGenBuffers();
                GlStateManager._glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, name);
                ARBBufferStorage.nglBufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, _size, MemoryUtil.NULL, BUFFER_CREATION_FLAGS);
                return new SsboInfo(GL30C.glMapBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, _size, BUFFER_MAP_FLAGS), name);
            });

            if (syncObj != MemoryUtil.NULL) {
                int waitReturn = GL32C.GL_UNSIGNALED;
                while (waitReturn != GL32C.GL_ALREADY_SIGNALED && waitReturn != GL32C.GL_CONDITION_SATISFIED) {
                    waitReturn = GL32C.glClientWaitSync(syncObj, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 1);
                }
            }

            for (int i = 0; i < entries.size(); i++) {
                MatrixStack.Entry entry = entries.get(i);
                ssbo.pointer().position(i * BakedMinecraftModels.STRUCT_SIZE);
                if (entry != null) {
                    Matrix4f model = entry.getModel();
                    ssbo.pointer().putFloat(model.a00).putFloat(model.a10).putFloat(model.a20).putFloat(model.a30)
                            .putFloat(model.a01).putFloat(model.a11).putFloat(model.a21).putFloat(model.a31)
                            .putFloat(model.a02).putFloat(model.a12).putFloat(model.a22).putFloat(model.a32)
                            .putFloat(model.a03).putFloat(model.a13).putFloat(model.a23).putFloat(model.a33);
                }
            }

            GL30C.glFlushMappedBufferRange(GL43.GL_SHADER_STORAGE_BUFFER, 0, ssboSize);

            if (syncObj != MemoryUtil.NULL) {
                GL32C.glDeleteSync(syncObj);
            }
            syncObj = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

            GL30.glBindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, 1, ssbo.name());

            RenderLayer layer = ((RenderLayerCreatedBufferBuilder) nestedBufferBuilder).getRenderLayer();
            if (layer == null) {
                throw new RuntimeException("This is bad");
            }

            layer.startDrawing();
            bmm$bakedVertices.setShader(matrices.peek().getModel(), RenderSystem.getProjectionMatrix(), BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL);
            layer.endDrawing();

            ci.cancel();
        }
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At(value = "INVOKE", shift = At.Shift.BEFORE, target = "Lnet/minecraft/client/util/math/MatrixStack;push()V"))
    public MatrixStack changeMatrixStack(MatrixStack stack) {
        if (bmm$initialModelPartForBaking == this && bmm$bakedVertices == null) {
            return new MatrixStack();
        }
        return stack;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V", at = @At("TAIL"))
    public void createVertexBufferRender(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (bmm$bakedVertices == null && bmm$usingSmartRenderer && bmm$initialModelPartForBaking == this) {
            BufferBuilder builder = BakedMinecraftModels.getNestedBufferBuilder(vertices);
            if (MinecraftClient.getInstance().getWindow() != null) {
                builder.end();
                bmm$bakedVertices = new VertexBuffer();
                bmm$bakedVertices.upload(builder);
                this.render(matrices, vertices, light, overlay, red, green, blue, alpha);
            }
        }

        if (bmm$initialModelPartForBaking == this) {
            bmm$initialModelPartForBaking = null;
            bmm$usingSmartRenderer = false;
        }
    }

    @Unique
    private void bmm$createMatrixTransformations(MatrixStack stack, ObjectArrayList<MatrixStack.Entry> entries) {
        stack.push();
        this.rotate(stack);
        while (entries.size() <= bmm$id) {
            entries.add(null);
        }
        if (this.visible) {
            entries.set(bmm$id, stack.peek());
        }
        for (ModelPart child : children.values()) {
            ((ModelPartMixin) (Object) child).bmm$createMatrixTransformations(stack, entries);
        }
        stack.pop();
    }
}
