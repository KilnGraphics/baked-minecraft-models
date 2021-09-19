/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.renderlayer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import graphics.kiln.bakedminecraftmodels.mixin.BufferRendererAccessor;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(RenderLayer.class)
public abstract class RenderLayerMixin extends RenderPhase {
    public RenderLayerMixin(String name, Runnable beginAction, Runnable endAction) {
        super(name, beginAction, endAction);
    }

    @Inject(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;startDrawing()V", shift = At.Shift.BEFORE), cancellable = true)
    private void trySkipSetState(BufferBuilder buffer, int cameraX, int cameraY, int cameraZ, CallbackInfo ci) {
        Pair<BufferBuilder.DrawArrayParameters, ByteBuffer> pair = buffer.popData();
        BufferBuilder.DrawArrayParameters drawArrayParameters = pair.getFirst();
        int count = drawArrayParameters.getCount();
        if (count > 0) {
            if (!RenderSystem.isOnRenderThreadOrInit()) {
                RenderSystem.recordRenderCall(() -> {
                    this.startDrawing();
                    BufferRendererAccessor.drawInternal(pair.getSecond(), drawArrayParameters.getMode(), drawArrayParameters.getVertexFormat(), drawArrayParameters.getCount(), drawArrayParameters.getElementFormat(), drawArrayParameters.getVertexCount(), drawArrayParameters.isTextured());
                    this.endDrawing();
                });
            } else {
                this.startDrawing();
                BufferRendererAccessor.drawInternal(pair.getSecond(), drawArrayParameters.getMode(), drawArrayParameters.getVertexFormat(), drawArrayParameters.getCount(), drawArrayParameters.getElementFormat(), drawArrayParameters.getVertexCount(), drawArrayParameters.isTextured());
                this.endDrawing();
            }
        }
        ci.cancel();
    }
}
