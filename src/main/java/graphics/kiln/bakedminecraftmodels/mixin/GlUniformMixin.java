/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin;

import java.nio.FloatBuffer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.gl.GlUniform;

@Mixin(GlUniform.class)
public class GlUniformMixin {
    @Shadow
    @Final
    private int count;

    @Redirect(method = "set([F)V", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put([F)Ljava/nio/FloatBuffer;"))
    public FloatBuffer set(FloatBuffer floatBuffer, float[] src) {
        return floatBuffer.put(src, 0, count);
    }
}
