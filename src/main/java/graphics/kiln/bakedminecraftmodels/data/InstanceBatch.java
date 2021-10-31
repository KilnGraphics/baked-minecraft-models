/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import net.minecraft.client.render.VertexFormat;

import java.util.ArrayList;
import java.util.List;

public class InstanceBatch {

    private final List<BakingData.PerInstanceData> instances;
    private final MatrixEntryList matrices;

    private int[] primitiveIndices;
    private int skippedPrimitives;

    private VertexFormat.IntType indexType;
    private long indexOffset;

    public InstanceBatch(int initialSize) {
        this.instances = new ArrayList<>(initialSize);
        this.matrices = new MatrixEntryList(initialSize);
    }

    public void reset() {
        instances.clear();
        matrices.clear();

        primitiveIndices = null;
        skippedPrimitives = 0;
    }

    public boolean isIndexed() {
        return primitiveIndices != null;
    }

    public MatrixEntryList getMatrices() {
        return matrices;
    }

    public void writeInstancesToBuffer(SectionedPersistentBuffer buffer) {
        for (BakingData.PerInstanceData perInstanceData : instances) {
            perInstanceData.writeToBuffer(buffer);
        }
    }

    public void addInstance(BakingData.PerInstanceData instanceData) {
        instances.add(instanceData);
    }

    public int size() {
        return instances.size();
    }

    public void setPrimitiveIndices(int[] primitiveIndices, int skippedPrimitives) {
        this.primitiveIndices = primitiveIndices;
        this.skippedPrimitives = skippedPrimitives;
    }

    public void writeIndicesToBuffer(SectionedPersistentBuffer buffer) {

    }

    public long getIndexOffset() {
        return indexOffset;
    }

    public VertexFormat.IntType getIndexType() {
        return indexType;
    }

}
