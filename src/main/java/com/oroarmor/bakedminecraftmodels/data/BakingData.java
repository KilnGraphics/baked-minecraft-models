package com.oroarmor.bakedminecraftmodels.data;

import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;

import java.util.Map;

public class BakingData {

    private final PriorityQueue<ModelInstanceData> modelInstancePool;
    private final Map<ModelType, ModelTypeData> modelTypeMap;
    private ModelTypeData currentModelTypeData;

    public BakingData() {
        this.modelInstancePool = new ObjectHeapPriorityQueue<>();
        this.modelTypeMap = new Object2ObjectOpenHashMap<>();
    }

    public void tryCreateCurrentModelTypeData(ModelType modelType) {
        currentModelTypeData = modelTypeMap.computeIfAbsent(modelType, unused -> new ModelTypeData(modelInstancePool));
    }

    public ModelTypeData getCurrentModelTypeData() {
        return currentModelTypeData;
    }

    public void reset() {
        for (ModelTypeData modelTypeData : modelTypeMap.values()) {
            modelTypeData.reset();
        }
        currentModelTypeData = null;
    }

    public void writeToPbos(SectionedPbo modelPbo, SectionedPbo partPbo) {
        for (ModelTypeData modelTypeData : modelTypeMap.values()) {
            modelTypeData.writeToPbos(modelPbo, partPbo);
        }
    }

}
