package graphics.kiln.bakedminecraftmodels.model;

import graphics.kiln.bakedminecraftmodels.data.BakingData;
import graphics.kiln.bakedminecraftmodels.data.MatrixEntryList;

import java.util.List;

public class InstanceBatch {

    private final List<BakingData.PerInstanceData> instances;
    private final MatrixEntryList matrices;

    private int[] primitiveSqDistances;
    private int skippedPrimitives;

    public InstanceBatch() {

    }
}
