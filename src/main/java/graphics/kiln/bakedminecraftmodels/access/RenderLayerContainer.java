/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.access;

import net.minecraft.client.render.RenderLayer;

public interface RenderLayerContainer {
    RenderLayer getRenderLayer();
    void setRenderLayer(RenderLayer renderLayer);
}
