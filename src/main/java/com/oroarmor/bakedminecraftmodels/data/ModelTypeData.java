package com.oroarmor.bakedminecraftmodels.data;

import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.nio.ByteBuffer;
import java.util.List;

public class ModelTypeData {

    private final PriorityQueue<ModelInstanceData> modelInstancePool;
    private final List<ModelInstanceData> modelInstanceList;
    private ModelInstanceData currentModelInstanceData;

    public ModelTypeData(PriorityQueue<ModelInstanceData> modelInstancePool) {
        this.modelInstancePool = modelInstancePool;
        this.modelInstanceList = new ObjectArrayList<>(64);
    }

    private ModelInstanceData getModelInstanceData() {
        if (!modelInstancePool.isEmpty()) {
            ModelInstanceData pooledObj = modelInstancePool.dequeue();
            pooledObj.reset();
            return pooledObj;
        } else {
            return new ModelInstanceData();
        }
    }

    public void createCurrentModelInstanceData() {
        currentModelInstanceData = getModelInstanceData();
        modelInstanceList.add(currentModelInstanceData);
    }

    public ModelInstanceData getCurrentModelInstanceData() {
        return currentModelInstanceData;
    }

    public int getInstanceCount() {
        return modelInstanceList.size();
    }

    public void reset() {
        for(ModelInstanceData modelInstanceData : modelInstanceList) {
            modelInstancePool.enqueue(modelInstanceData);
        }
        modelInstanceList.clear();
        currentModelInstanceData = null;
    }

    public void writeToPbos(SectionedPbo modelPbo, SectionedPbo partPbo) {
        for (ModelInstanceData modelInstanceData : modelInstanceList) {
            modelInstanceData.writeToPbos(modelPbo, partPbo);
        }
    }

}
