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
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.render.RenderLayer;

import java.util.Collection;
import java.util.Map;

public class ModelTypeData {

    private final Map<RenderLayer, ModelRenderSubtypeData> modelRenderSubtypeMap;
    private final VboBackedModel model;
    private ModelRenderSubtypeData currentSubtypeData;

    public ModelTypeData(VboBackedModel model) {
        this.model = model;
        this.modelRenderSubtypeMap = new Object2ObjectOpenHashMap<>();
    }

    public void tryCreateCurrentSubtypeData(RenderLayer renderLayer) {
        currentSubtypeData = modelRenderSubtypeMap.computeIfAbsent(renderLayer, ModelRenderSubtypeData::new);
    }

    public ModelRenderSubtypeData getCurrentSubtypeData() {
        return currentSubtypeData;
    }

    public Collection<ModelRenderSubtypeData> getAllSubtypeData() {
        return modelRenderSubtypeMap.values();
    }

    public VboBackedModel getModel() {
        return model;
    }

    public void reset() {
        for (ModelRenderSubtypeData subtype : modelRenderSubtypeMap.values()) {
            subtype.reset();
        }
        currentSubtypeData = null;
    }

    public void writeToBuffer(SectionedPersistentBuffer modelPbo, SectionedPersistentBuffer partPbo) {
        for (ModelRenderSubtypeData subtype : modelRenderSubtypeMap.values()) {
            subtype.writeToBuffer(modelPbo, partPbo);
        }
    }

}
