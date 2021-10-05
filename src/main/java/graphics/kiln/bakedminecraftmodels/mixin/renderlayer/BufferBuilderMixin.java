/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.renderlayer;

import graphics.kiln.bakedminecraftmodels.access.RenderLayerContainer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BufferBuilder.class)
public class BufferBuilderMixin implements RenderLayerContainer {

    private RenderLayer bmm$renderLayer;

    @Override
    public RenderLayer getRenderLayer() {
        return bmm$renderLayer;
    }

    @Override
    public void setRenderLayer(RenderLayer renderLayer) {
        bmm$renderLayer = renderLayer;
    }
}
