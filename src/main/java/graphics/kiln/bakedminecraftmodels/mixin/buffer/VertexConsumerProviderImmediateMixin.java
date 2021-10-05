/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.buffer;

import graphics.kiln.bakedminecraftmodels.access.RenderLayerContainer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VertexConsumerProvider.Immediate.class)
public abstract class VertexConsumerProviderImmediateMixin {
    @Inject(method = "getBuffer", at = @At("RETURN"))
    private void attachRenderLayerToBuffer(RenderLayer renderLayer, CallbackInfoReturnable<VertexConsumer> cir) {
        VertexConsumer consumer = cir.getReturnValue();
        if (consumer instanceof RenderLayerContainer renderLayerContainer) {
            renderLayerContainer.setRenderLayer(renderLayer);
        }
    }
}
