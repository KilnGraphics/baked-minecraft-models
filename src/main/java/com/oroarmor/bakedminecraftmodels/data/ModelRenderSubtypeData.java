package com.oroarmor.bakedminecraftmodels.data;

import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import net.minecraft.client.render.RenderLayer;

import java.util.LinkedList;
import java.util.List;

public class ModelRenderSubtypeData {

    private final List<ModelInstanceData> modelInstanceList;
    private final RenderLayer renderLayer;
    private ModelInstanceData currentModelInstanceData;

    public ModelRenderSubtypeData(RenderLayer renderLayer) {
        this.renderLayer = renderLayer;
        this.modelInstanceList = new LinkedList<>();
    }

    public void createCurrentModelInstanceData() {
        currentModelInstanceData = new ModelInstanceData();
        modelInstanceList.add(currentModelInstanceData);
    }

    public ModelInstanceData getCurrentModelInstanceData() {
        return currentModelInstanceData;
    }

    public int getInstanceCount() {
        return modelInstanceList.size();
    }

    public RenderLayer getRenderLayer() {
        return renderLayer;
    }

    public void reset() { // unused for now
        modelInstanceList.clear();
        currentModelInstanceData = null;
    }

    public void writeToBuffer(SectionedPersistentBuffer modelPbo, SectionedPersistentBuffer partPbo) {
        for (ModelInstanceData modelInstanceData : modelInstanceList) {
            modelInstanceData.writeToBuffer(modelPbo, partPbo);
        }
    }
}
