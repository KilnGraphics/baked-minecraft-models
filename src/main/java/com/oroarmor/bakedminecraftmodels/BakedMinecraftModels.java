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

import com.oroarmor.bakedminecraftmodels.debug.ModelExporter;
import net.fabricmc.api.ClientModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BakedMinecraftModels implements ClientModInitializer {
    public static final String MOD_ID = "baked_minecraft_models";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    private static final boolean EXPORT_MODELS_TO_OBJ = false;
    private static final boolean ENABLE_RENDERDOC = true;

    // RenderDoc Vertex Format:
    /*
     vec3 pos
     vec2 uv
     byte3 normal
     byte padding
     int id
    */

    // RenderDoc Part SSBO Format:
    /*
     mat4 modelViewMat;
    */

    // RenderDoc Model SSBO Format:
    /*
     vec4 Color;
     ivec2 UV1;
     ivec2 UV2;
     int partOffset; // TODO: make this a uint if needed
    */

    @Override
    public void onInitializeClient() {
        if (ENABLE_RENDERDOC) {
            try {
                System.loadLibrary("renderdoc");
            } catch (Throwable e) {
                System.err.println("Unable to load renderdoc");
            }
        }

        if (EXPORT_MODELS_TO_OBJ) {
            ModelExporter.exportDefaultModelsToOBJ();
        }
    }

}
