/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.model;

import graphics.kiln.bakedminecraftmodels.data.MatrixEntryList;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;

import java.nio.ByteBuffer;

public interface VboBackedModel {
    VertexBuffer getBakedVertices();

    /**
     * Get the pre-processed index data, ready for depth sorting.
     * <p>
     * This is actually the concatenation of two somewhat separate things. The first is the index data itself - it's
     * stored in the standard way, with the format and draw mode being specified by {@link #getBakedIndexMetadata()}.
     * <p>
     * The second is the mean centre position of each primitive. It's the mean of all the vertices in the primitive, and
     * by finding the distance between it and a point based on the inverse of the MVP matrix, can be used to find the
     * distance between the camera and the primitive. This is used for depth sorting of transparent vertices.
     * <p>
     * This data carefully packed together for sake of performance - we'll be reading this a LOT (since every
     * frame we have to sort the vertices for every transparent object). We'll have to find the distance between each
     * vector to a point based off the inverted MVP transform, and then do the sort.
     * <p>
     * Note again for sake of performance, there may be a padding gap between the two blocks for alignment, to ensure
     * the float accesses are four-byte aligned.
     */
    ByteBuffer getBakedIndexData();

    BakedIndexMetadata getBakedIndexMetadata();

    MatrixEntryList getCurrentMatrices();

    void setMatrixEntryList(MatrixEntryList matrixEntryList);

    record BakedIndexMetadata(VertexFormat.IntType indexFormat, VertexFormat.DrawMode drawMode, int indexCount,
                              int primitiveCount, int positionsOffset) {
        public int indexStride() {
            return indexFormat.size * 3; // always use triangles - was drawMode.vertexCount;
        }

        public int indexDataSize() {
            return indexStride() * primitiveCount;
        }
    }
}
