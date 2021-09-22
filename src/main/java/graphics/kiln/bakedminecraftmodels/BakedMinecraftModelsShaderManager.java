/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels;

import java.io.IOException;

import net.minecraft.client.gl.GlUniform;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.Shader;
import net.minecraft.resource.ResourceManager;

public class BakedMinecraftModelsShaderManager {
    public static @Nullable Shader SMART_ENTITY_CUTOUT_NO_CULL;
    public static @Nullable GlUniform INSTANCE_OFFSET_1;

    public static @Nullable Shader SMART_ENTITY_TRANSLUCENT;

    public static void loadShaders(ResourceManager manager) {
        try {
            SMART_ENTITY_CUTOUT_NO_CULL = new Shader(manager, "rendertype_entity_cutout_no_cull_smart", BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
            INSTANCE_OFFSET_1 = SMART_ENTITY_CUTOUT_NO_CULL.getUniform("InstanceOffset");

            SMART_ENTITY_TRANSLUCENT = new Shader(manager, "rendertype_entity_translucent", BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
