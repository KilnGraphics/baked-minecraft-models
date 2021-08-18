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

package com.oroarmor.bakedminecraftmodels;

import java.nio.ByteOrder;

import com.oroarmor.bakedminecraftmodels.debug.ModelExporter;
import com.oroarmor.bakedminecraftmodels.mixin.buffer.SpriteTexturedVertexConsumerAccessor;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;

import net.fabricmc.api.ClientModInitializer;

public class BakedMinecraftModels implements ClientModInitializer {
    public static final String MOD_ID = "baked_minecraft_models";
    public static final int STRUCT_SIZE = 16 * Float.BYTES; //(16 + 12) * Float.BYTES;
    private static final boolean EXPORT_MODELS_TO_OBJ = false;

    // RenderDoc Vertex Format:
    /*
     vec3 pos
     vec2 uv
     byte3 normal
     byte padding
     int id
    */

    // RenderDoc SSBO Format:
    /*
     mat4 modelViewMat;
     mat3x4 normalMat; // ignore 0s at the bottom
    */

    @Override
    public void onInitializeClient() {
        try {
            System.loadLibrary("renderdoc");
        } catch (Throwable e) {
            System.err.println("Unable to load renderdoc");
        }

        if (EXPORT_MODELS_TO_OBJ) {
            ModelExporter.exportDefaultModelsToOBJ();
        }
    }

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    public static BufferBuilder getNestedBufferBuilder(VertexConsumer consumer) {
        return consumer instanceof SpriteTexturedVertexConsumer ?
                (BufferBuilder) ((SpriteTexturedVertexConsumerAccessor) consumer).getParent() :
                (BufferBuilder) consumer;
    }
}
