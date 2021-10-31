package graphics.kiln.bakedminecraftmodels.data;

import java.util.ArrayList;
import java.util.List;

public class InstanceBatch {

    public final List<BakingData.PerInstanceData> instances;
    public final MatrixEntryList matrices;

    public int[] primitiveSqDistances;
    public int skippedPrimitives;

    public InstanceBatch(int initialSize) {
        this.instances = new ArrayList<>(initialSize);
        this.matrices = new MatrixEntryList(initialSize);
    }

    public void reset() {
        instances.clear();
        matrices.clear();

        primitiveSqDistances = null;
        skippedPrimitives = 0;
    }

    public boolean isIndexed() {
        return primitiveSqDistances != null;
    }
}
