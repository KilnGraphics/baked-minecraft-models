/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin;

import graphics.kiln.bakedminecraftmodels.BakedMinecraftModelsShaderManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.resource.ResourceManager;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "loadShaders", at = @At("RETURN"))
    public void loadShaders(ResourceManager manager, CallbackInfo ci) {
        BakedMinecraftModelsShaderManager.loadShaders(manager);
    }
}
