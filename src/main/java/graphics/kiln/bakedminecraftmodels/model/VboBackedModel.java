/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.model;

import net.minecraft.client.gl.VertexBuffer;

public interface VboBackedModel {
    VertexBuffer getBakedVertices();
    int getVertexCount();
    float[] getPrimitivePositions();
    int[] getPrimitivePartIds();
}
