/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.renderlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.RenderPhase;

@Mixin(RenderPhase.class)
public interface RenderPhaseAccessor {
    @Accessor
    String getName();

    @Accessor("COLOR_MASK")
    static RenderPhase.WriteMaskState getColorMask() {
        throw new RuntimeException("how");
    }
}
