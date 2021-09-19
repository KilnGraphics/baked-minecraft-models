/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.renderlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.RenderLayer;

@Mixin(RenderLayer.class)
public interface RenderLayerAccessor extends RenderPhaseAccessor {

    @Accessor
    boolean getHasCrumbling();

    @Accessor
    boolean getTranslucent(); // This doesn't actually mean translucent, yarn just doesn't have a good name for this.
}
