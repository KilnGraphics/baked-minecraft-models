/*
 * MIT License
 *
 * Copyright (c) 2021 OroArmor (Eli Orona), Blaze4D
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oroarmor.bakedminecraftmodels.data;

import com.oroarmor.bakedminecraftmodels.model.VboBackedModel;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.RenderLayer;

import java.util.LinkedList;
import java.util.List;

public class ModelTypeData {

    private final PriorityQueue<ModelInstanceData> modelInstancePool;
    private final List<ModelInstanceData> modelInstanceList;
    private final VboBackedModel model;
    private ModelInstanceData currentModelInstanceData;

    private RenderLayer renderLayer;

    public ModelTypeData(PriorityQueue<ModelInstanceData> modelInstancePool, VboBackedModel model) {
        this.model = model;
        this.modelInstancePool = modelInstancePool;
        this.modelInstanceList = new LinkedList<>();
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

    /**
     * After this is set once, until it's reset, this will ignore any subsequent
     * calls to this method.
     */
    public void setRenderLayer(RenderLayer renderLayer) {
        if (this.renderLayer == null) {
            this.renderLayer = renderLayer;
        }
    }

    public RenderLayer getRenderLayer() {
        return renderLayer;
    }

    public VboBackedModel getModel() {
        return model;
    }

    public void reset() {
        for(ModelInstanceData modelInstanceData : modelInstanceList) {
            modelInstancePool.enqueue(modelInstanceData);
        }
        modelInstanceList.clear();
        currentModelInstanceData = null;
        renderLayer = null;
    }

    public void writeToPbos(SectionedPbo modelPbo, SectionedPbo partPbo) {
        if (renderLayer != null) {
            for (ModelInstanceData modelInstanceData : modelInstanceList) {
                modelInstanceData.writeToPbos(modelPbo, partPbo);
            }
        }
    }

}
