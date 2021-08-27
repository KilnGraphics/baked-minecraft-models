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

import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import it.unimi.dsi.fastutil.PriorityQueue;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;

import java.util.Collection;
import java.util.Map;

public class BakingData {

    private final PriorityQueue<ModelInstanceData> modelInstancePool;
    private final Map<ModelType, ModelTypeData> modelTypeMap;
    private ModelTypeData currentModelTypeData;

    public BakingData() {
        this.modelInstancePool = new ObjectArrayFIFOQueue<>();
        this.modelTypeMap = new Object2ObjectOpenHashMap<>();
    }

    public void tryCreateCurrentModelTypeData(ModelType modelType) {
        currentModelTypeData = modelTypeMap.computeIfAbsent(modelType, modelType_ -> new ModelTypeData(modelInstancePool, modelType_.model()));
    }

    public ModelTypeData getCurrentModelTypeData() {
        return currentModelTypeData;
    }

    public Collection<ModelTypeData> getAllModelTypeData() {
        return modelTypeMap.values();
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
