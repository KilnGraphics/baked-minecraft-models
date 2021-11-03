/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import com.google.common.collect.Iterators;
import graphics.kiln.bakedminecraftmodels.BakedMinecraftModels;
import graphics.kiln.bakedminecraftmodels.gl.GlSsboRenderDispacher;
import graphics.kiln.bakedminecraftmodels.mixin.buffer.VertexBufferAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.RenderPhaseAccessor;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;

import java.io.Closeable;
import java.util.*;

public class BakingData implements Closeable, Iterable<Map<RenderLayer, Map<VboBackedModel, InstanceBatch>>> {

    private static final int INITIAL_BATCH_CAPACITY = 16;

    /**
     * Slice current instances on transparency where order is required.
     * Looks more correct, but may impact performance greatly.
     */
    public static final boolean TRANSPARENCY_SLICING = true;

    private final Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> opaqueSection;
    /**
     * Each map in the deque represents a separate ordered section, which is required for transparency ordering.
     * For each model in the map, it has its own map where each RenderLayer has a list of instances. This is
     * because we can only batch instances with the same RenderLayer and model.
     */
    private final Deque<Map<RenderLayer, Map<VboBackedModel, InstanceBatch>>> orderedTransparencySections;

    private final Set<AutoCloseable> closeables;
    private final SectionedPersistentBuffer modelPersistentSsbo;
    private final SectionedPersistentBuffer partPersistentSsbo;
    private final SectionedPersistentBuffer translucencyPersistentEbo;
    private final Deque<InstanceBatch> batchPool;

    private RenderPhase.Transparency previousTransparency;

    public BakingData(SectionedPersistentBuffer modelPersistentSsbo, SectionedPersistentBuffer partPersistentSsbo, SectionedPersistentBuffer translucencyPersistentEbo) {
        this.modelPersistentSsbo = modelPersistentSsbo;
        this.partPersistentSsbo = partPersistentSsbo;
        this.translucencyPersistentEbo = translucencyPersistentEbo;
        opaqueSection = new LinkedHashMap<>();
        orderedTransparencySections = new ArrayDeque<>(64);
        closeables = new ObjectOpenHashSet<>();
        batchPool = new ArrayDeque<>(64);
    }

    @SuppressWarnings("ConstantConditions")
    public InstanceBatch getOrCreateInstanceBatch(RenderLayer renderLayer, VboBackedModel model) {
        Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> renderSection;
        // we still use linked maps in here to try to preserve patterns for things that rely on rendering in order not based on transparency.
        MultiPhaseParametersAccessor multiPhaseParameters = (MultiPhaseParametersAccessor) (Object) ((MultiPhaseRenderPassAccessor) renderLayer).getPhases();
        RenderPhase.Transparency currentTransparency = multiPhaseParameters.getTransparency();
        if (!(currentTransparency instanceof RenderPhaseAccessor currentTransparencyAccessor) || currentTransparencyAccessor.getName().equals("no_transparency")) {
            renderSection = opaqueSection;
        } else {
            if (orderedTransparencySections.size() == 0) {
                addNewSplit();
            } else if (TRANSPARENCY_SLICING && previousTransparency instanceof RenderPhaseAccessor previousTransparencyAccessor) {
                String currentTransparencyName = currentTransparencyAccessor.getName();
                String previousTransparencyName = previousTransparencyAccessor.getName();
                if (currentTransparencyName.equals(previousTransparencyName)) {
                    addNewSplit();
                }
            }

            renderSection = orderedTransparencySections.peekLast();
        }
        previousTransparency = currentTransparency;

        return renderSection
                .computeIfAbsent(renderLayer, unused -> new LinkedHashMap<>())
                .computeIfAbsent(model, model1 -> {
                    InstanceBatch recycledBatch = batchPool.pollFirst();
                    // we want to keep the lambda in this format, so we create only one instead of two
                    //noinspection ReplaceNullCheck
                    if (recycledBatch != null) {
                        return recycledBatch;
                    } else {
                        return new InstanceBatch(model1, GlSsboRenderDispacher.requiresIndexing(multiPhaseParameters), INITIAL_BATCH_CAPACITY, partPersistentSsbo);
                    }
                });
    }

    public void recycleInstanceBatch(InstanceBatch instanceBatch) {
        instanceBatch.reset();
        batchPool.add(instanceBatch);
    }

    private void addNewSplit() {
        orderedTransparencySections.add(new LinkedHashMap<>());
    }

    private void writeSplitData(Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> splitData) {
        for (Map<VboBackedModel, InstanceBatch> perRenderLayerData : splitData.values()) {
            for (Map.Entry<VboBackedModel, InstanceBatch> perModelData : perRenderLayerData.entrySet()) {
                InstanceBatch instanceBatch = perModelData.getValue();
                VertexBufferAccessor vertexBufferAccessor = (VertexBufferAccessor) perModelData.getKey().getBakedVertices();
                instanceBatch.writeInstancesToBuffer(modelPersistentSsbo);
                instanceBatch.writeIndicesToBuffer(vertexBufferAccessor.getDrawMode(), translucencyPersistentEbo);
            }
        }
    }

    public void writeData() {
        writeSplitData(opaqueSection);

        for (Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> transparencySection : orderedTransparencySections) {
            writeSplitData(transparencySection);
        }
    }

    public void addCloseable(AutoCloseable closeable) {
        closeables.add(closeable);
    }

    public Iterator<Map<RenderLayer, Map<VboBackedModel, InstanceBatch>>> iterator() {
        return Iterators.concat(Iterators.singletonIterator(opaqueSection), orderedTransparencySections.iterator());
    }

    public boolean isEmptyShallow() {
        return opaqueSection.isEmpty() && orderedTransparencySections.isEmpty();
    }

    @SuppressWarnings("unused")
    public boolean isEmptyDeep() {
        for (Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> perOrderedSectionData : this) {
            for (Map<VboBackedModel, InstanceBatch> perRenderLayerData : perOrderedSectionData.values()) {
                for (InstanceBatch instanceBatch : perRenderLayerData.values()) {
                    if (instanceBatch.size() > 0) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void reset() {
        opaqueSection.clear();
        orderedTransparencySections.clear();
    }

    @Override
    public void close() {
        for (AutoCloseable closeable : closeables) {
            try {
                closeable.close();
            } catch (Exception e) {
                BakedMinecraftModels.LOGGER.error("Error closing baking data closeables", e);
            }
        }
        closeables.clear();
    }

}
