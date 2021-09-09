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
import com.oroarmor.bakedminecraftmodels.data.BakingData;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.SpriteTexturedVertexConsumerAccessor;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedSyncObjects;
import com.oroarmor.bakedminecraftmodels.vertex.SmartBufferBuilderWrapper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBShaderStorageBufferObject;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.system.MemoryUtil;

public class GlobalModelUtils {

    public static final long MODEL_STRUCT_SIZE = (4 * Float.BYTES) + (2 * Integer.BYTES) + (2 * Integer.BYTES) + (3 * Float.BYTES) + Integer.BYTES;
    public static final long PART_STRUCT_SIZE = 16 * Float.BYTES;

    public static final MatrixStack.Entry IDENTITY_STACK_ENTRY = new MatrixStack().peek();

    public static final int BUFFER_CREATION_FLAGS = GL30C.GL_MAP_WRITE_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    public static final int BUFFER_MAP_FLAGS = GL30C.GL_MAP_WRITE_BIT | GL30C.GL_MAP_FLUSH_EXPLICIT_BIT | ARBBufferStorage.GL_MAP_PERSISTENT_BIT;

    public static final int BUFFER_SECTIONS = 3;

    public static final int ENTITY_LIMIT = 8192;

    public static final long PART_PBO_SIZE = ENTITY_LIMIT * 16 * PART_STRUCT_SIZE; // assume each entity has on average 16 parts
    public static final long MODEL_PBO_SIZE = ENTITY_LIMIT * MODEL_STRUCT_SIZE;

    public static final BakingData bakingData = new BakingData();

    public static final SmartBufferBuilderWrapper VBO_BUFFER_BUILDER = new SmartBufferBuilderWrapper(new BufferBuilder(32768)); // just some random initial capacity lol

    // TODO: MOVE THESE AS SOON AS POSSIBLE FOR ABSTRACTION!!!
    private static SectionedPersistentBuffer PART_PBO;
    private static SectionedPersistentBuffer MODEL_PBO;
    public static SectionedSyncObjects SYNC_OBJECTS = new SectionedSyncObjects(GlobalModelUtils.BUFFER_SECTIONS);

    public static SectionedPersistentBuffer getOrCreatePartPbo() {
        if (PART_PBO == null) {
            PART_PBO = createSsboPersistentBuffer(PART_PBO_SIZE);
        }
        return PART_PBO;
    }

    public static SectionedPersistentBuffer getOrCreateModelPbo() {
        if (MODEL_PBO == null) {
            MODEL_PBO = createSsboPersistentBuffer(MODEL_PBO_SIZE);
        }
        return MODEL_PBO;
    }

    private static SectionedPersistentBuffer createSsboPersistentBuffer(long ssboSize) {
        int name = GlStateManager._glGenBuffers();
        GlStateManager._glBindBuffer(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, name);
        long fullSize = ssboSize * GlobalModelUtils.BUFFER_SECTIONS;
        ARBBufferStorage.nglBufferStorage(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, fullSize, MemoryUtil.NULL, GlobalModelUtils.BUFFER_CREATION_FLAGS);
        return new SectionedPersistentBuffer(
                GL30C.nglMapBufferRange(ARBShaderStorageBufferObject.GL_SHADER_STORAGE_BUFFER, 0, fullSize, GlobalModelUtils.BUFFER_MAP_FLAGS),
                name,
                GlobalModelUtils.BUFFER_SECTIONS,
                ssboSize
        );
    }

    public static VertexConsumer getNestedBufferBuilder(VertexConsumer consumer) { // TODO: add more possibilities with this method, ex outline consumers
        return consumer instanceof SpriteTexturedVertexConsumer ?
                (BufferBuilder) ((SpriteTexturedVertexConsumerAccessor) consumer).getParent() :
                consumer;
    }

}
