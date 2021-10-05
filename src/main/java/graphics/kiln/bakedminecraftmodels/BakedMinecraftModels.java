/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels;

import graphics.kiln.bakedminecraftmodels.debug.ModelExporter;
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
     uint id
    */

    // RenderDoc Part SSBO Format:
    /*
     mat4 modelViewMat;
     mat3x4 normalMat;
    */

    // RenderDoc Model SSBO Format:
    /*
     vec4 Color;
     ivec2 UV1;
     ivec2 UV2;
     vec3 padding;
     uint partOffset;
    */

    @Override
    public void onInitializeClient() {
        if (ENABLE_RENDERDOC) {
            try {
                System.loadLibrary("renderdoc");
            } catch (Throwable e) {
                LOGGER.error("Unable to load renderdoc");
            }
        }

        if (EXPORT_MODELS_TO_OBJ) {
            ModelExporter.exportDefaultModelsToOBJ();
        }
    }

}
