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

import java.io.IOException;

import net.minecraft.client.gl.GlUniform;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.Shader;
import net.minecraft.resource.ResourceManager;

public class BakedMinecraftModelsShaderManager {
    public static @Nullable Shader SMART_ENTITY_CUTOUT_NO_CULL;
    public static @Nullable GlUniform INSTANCE_OFFSET;

    public static void loadShaders(ResourceManager manager) {
        try {
            SMART_ENTITY_CUTOUT_NO_CULL = new Shader(manager, "rendertype_entity_cutout_no_cull_buffer_tex", BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
            INSTANCE_OFFSET = SMART_ENTITY_CUTOUT_NO_CULL.getUniform("InstanceOffset");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
