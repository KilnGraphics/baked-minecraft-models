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

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsRenderLayerManager;
import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;
import com.oroarmor.bakedminecraftmodels.access.RenderLayerCreatedBufferBuilder;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.SpriteTexturedVertexConsumerAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.List;

public class GlobalModelUtils {

    public static final int STRUCT_SIZE = 16 * Float.BYTES;

    public static final Matrix4f IDENTITY_MATRIX;
    public static final ByteBuffer IDENTITY_MATRIX_BUFFER;

    static {
        IDENTITY_MATRIX = new Matrix4f();
        IDENTITY_MATRIX.loadIdentity();
        FloatBuffer matrixBuffer = MemoryUtil.memAllocFloat(16);
        IDENTITY_MATRIX.writeColumnMajor(matrixBuffer);
        IDENTITY_MATRIX_BUFFER = MemoryUtil.memByteBuffer(MemoryUtil.memAddress(matrixBuffer), matrixBuffer.capacity());
    }

    public static final int BUFFER_CREATION_FLAGS = GL30C.GL_MAP_WRITE_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    public static final int BUFFER_MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_FLUSH_EXPLICIT_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    public static final int BUFFER_SECTIONS = 3;

    public static final Int2ObjectMap<SectionedPbo> SIZE_TO_GL_BUFFER_POINTER = new Int2ObjectOpenHashMap<>();

    public static final MatrixStack BAKING_MATRIX_STACK = new MatrixStack();

    public static List<Matrix4f> currentMatrices = new ObjectArrayList<>();
    public static SectionedPbo currentPbo; // TODO: stop using a matrix list and update on the fly

    public static BufferBuilder getNestedBufferBuilder(VertexConsumer consumer) {
        return consumer instanceof SpriteTexturedVertexConsumer ?
                (BufferBuilder) ((SpriteTexturedVertexConsumerAccessor) consumer).getParent() :
                (BufferBuilder) consumer;
    }

    public static boolean isSmartBufferBuilder(BufferBuilder nestedBuilder) {
        // what have i done
        return ((BufferBuilderAccessor) nestedBuilder)
                .getFormat()
                .equals(BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT)
                && ((MultiPhaseParametersAccessor) (Object) ((MultiPhaseRenderPassAccessor) ((RenderLayerCreatedBufferBuilder) nestedBuilder)
                .getRenderLayer())
                .getPhases())
                .getShader()
                .equals(BakedMinecraftModelsRenderLayerManager.SMART_ENTITY_CUTOUT_NO_CULL_PHASE);
    }

}
