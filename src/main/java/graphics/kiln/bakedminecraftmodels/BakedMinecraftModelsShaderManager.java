/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.Shader;
import net.minecraft.resource.ResourceManager;

public class BakedMinecraftModelsShaderManager {
    public static @Nullable Shader ENTITY_CUTOUT_NO_CULL_INSTANCED;
    public static @Nullable Shader ENTITY_TRANSLUCENT_BATCHED;

    public static void loadShaders(ResourceManager manager) {
        try {
            ENTITY_CUTOUT_NO_CULL_INSTANCED = new Shader(manager, "rendertype_entity_cutout_no_cull_instanced", BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
            ENTITY_TRANSLUCENT_BATCHED = new Shader(manager, "rendertype_entity_translucent_batched", BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
