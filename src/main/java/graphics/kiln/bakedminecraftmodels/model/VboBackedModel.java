/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.model;

import graphics.kiln.bakedminecraftmodels.data.MatrixEntryList;
import net.minecraft.client.gl.VertexBuffer;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public interface VboBackedModel {
    VertexBuffer getBakedVertices();
    int[] getBakedIndices();
    MatrixEntryList getCurrentMatrices();
    void setMatrixEntryList(MatrixEntryList matrixEntryList);
}
