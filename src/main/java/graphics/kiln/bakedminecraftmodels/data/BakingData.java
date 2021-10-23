/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import com.google.common.collect.Iterators;
import graphics.kiln.bakedminecraftmodels.BakedMinecraftModels;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.RenderPhaseAccessor;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import io.netty.util.internal.shaded.org.jctools.queues.ConcurrentCircularArrayQueue;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class BakingData implements Closeable, Iterable<Map<RenderLayer, Map<VboBackedModel, List<?>>>> {

    /**
     * Slice current instances on transparency where order is required.
     * Looks more correct, but may impact performance greatly.
     */
    public static final boolean TRANSPARENCY_SLICING = true;

    private final Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> opaqueSection;
    /**
     * Each map in the deque represents a separate ordered section, which is required for transparency ordering.
     * For each model in the map, it has its own map where each RenderLayer has a list of instances. This is
     * because we can only batch instances with the same RenderLayer and model.
     */
    private final Deque<Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>>> orderedTransparencySections;

    private final Set<AutoCloseable> closeables;
    private final SectionedPersistentBuffer modelPbo;
    private final SectionedPersistentBuffer partPbo;
    private final Deque<MatrixEntryList> matrixEntryListPool;

    private RenderPhase.Transparency previousTransparency;

    public BakingData(SectionedPersistentBuffer modelPbo, SectionedPersistentBuffer partPbo) {
        this.modelPbo = modelPbo;
        this.partPbo = partPbo;
        opaqueSection = new LinkedHashMap<>();
        orderedTransparencySections = new ArrayDeque<>(256);
        closeables = new ObjectOpenHashSet<>();
        matrixEntryListPool = new ArrayDeque<>();
    }

    public void addInstance(VboBackedModel model, RenderLayer renderLayer, MatrixStack.Entry baseMatrixEntry, float red, float green, float blue, float alpha, int overlay, int light) {
        int overlayX = overlay & 0xFFFF;
        int overlayY = overlay >> 16 & 0xFFFF;
        int lightX = light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);
        int lightY = light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);

        Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> renderSection;
        // we still use linked maps in here to try to preserve patterns for things that rely on rendering in order not based on transparency.
        RenderPhase.Transparency currentTransparency = ((MultiPhaseParametersAccessor) (Object) (((MultiPhaseRenderPassAccessor) renderLayer).getPhases())).getTransparency();
        if (!(currentTransparency instanceof RenderPhaseAccessor currentTransparencyAccessor) || currentTransparencyAccessor.getName().equals("no_transparency")) {
            renderSection = opaqueSection;
        } else {
            if (orderedTransparencySections.size() == 0) {
                addNewSplit();
            } else if (TRANSPARENCY_SLICING && previousTransparency instanceof RenderPhaseAccessor previousTransparencyAccessor) {
                String currentTransparencyName = currentTransparencyAccessor.getName();
                String previousTransparencyName = previousTransparencyAccessor.getName();
                // TODO comment
                //noinspection StringEquality - The name strings are compile-time constants and will be interned
                if (currentTransparencyName == previousTransparencyName) {
                    addNewSplit();
                }
            }

            renderSection = orderedTransparencySections.peekLast();
        }
        previousTransparency = currentTransparency;

        MatrixEntryList matrixEntryList = model.getCurrentMatrices();
        // this can happen if the model didn't render any modelparts,
        // in which case it makes sense to not try to render it anyway.
        if (matrixEntryList == null) return;
        model.setMatrixEntryList(null);
        long futurePartIndex = matrixEntryList.writeToBuffer(partPbo, baseMatrixEntry);
        matrixEntryList.clear();
        matrixEntryListPool.offerLast(matrixEntryList);

        // TODO: make this whole thing concurrent if it needs to be
        renderSection
                .computeIfAbsent(renderLayer, unused -> new LinkedHashMap<>())
                .computeIfAbsent(model, unused -> new LinkedList<>()) // we use a LinkedList here because ArrayList takes a long time to grow
                .add(new BakingData.PerInstanceData(futurePartIndex, red, green, blue, alpha, overlayX, overlayY, lightX, lightY));
    }

    private void addNewSplit() {
        orderedTransparencySections.add(new LinkedHashMap<>());
    }

    private void writeSplitData(Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> splitData) {
        for (Map<VboBackedModel, List<PerInstanceData>> perRenderLayerData : splitData.values()) {
            for (List<PerInstanceData> perModelData : perRenderLayerData.values()) {
                for (PerInstanceData perInstanceData : perModelData) {
                    perInstanceData.writeToBuffer(modelPbo);
                }
            }
        }
    }

    public void addPartMatrix(VboBackedModel model, int partId, MatrixStack.Entry matrixEntry) {
        MatrixEntryList list = model.getCurrentMatrices();
        if (list == null) {
            MatrixEntryList recycledList = matrixEntryListPool.pollFirst();
            if (recycledList != null) {
                list = recycledList;
            } else {
                list = new MatrixEntryList(partId);
            }
            model.setMatrixEntryList(list);
        }

        list.set(partId, matrixEntry);
    }

    public void writeData() {
        // TODO OPT: re-make this async by returning pointers to buffer sections and making things atomic

        writeSplitData(opaqueSection);

        for (Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> transparencySection : orderedTransparencySections) {
            writeSplitData(transparencySection);
        }
    }

    public void addCloseable(AutoCloseable closeable) {
        closeables.add(closeable);
    }

    public Iterator<Map<RenderLayer, Map<VboBackedModel, List<?>>>> iterator() {
        return (Iterator<Map<RenderLayer, Map<VboBackedModel, List<?>>>>) (Object) Iterators.concat(Iterators.singletonIterator(opaqueSection), orderedTransparencySections.iterator());
    }

    public boolean isEmptyShallow() {
        return opaqueSection.isEmpty() && orderedTransparencySections.isEmpty();
    }

    public boolean isEmptyDeep() {
        for (Map<RenderLayer, Map<VboBackedModel, List<?>>> perOrderedSectionData : this) {
            for (Map<VboBackedModel, List<?>> perRenderLayerData : perOrderedSectionData.values()) {
                for (List<?> perModelData : perRenderLayerData.values()) {
                    if (perModelData.size() > 0) {
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
    }

    private record PerInstanceData(long partArrayIndex, float red, float green, float blue, float alpha, int overlayX,
                                   int overlayY, int lightX, int lightY) {

        public void writeToBuffer(SectionedPersistentBuffer modelPbo) {
            long positionOffset = modelPbo.getPositionOffset().getAndAdd(GlobalModelUtils.MODEL_STRUCT_SIZE);
            long pointer = modelPbo.getSectionedPointer() + positionOffset;
            MemoryUtil.memPutFloat(pointer, red);
            MemoryUtil.memPutFloat(pointer + 4, green);
            MemoryUtil.memPutFloat(pointer + 8, blue);
            MemoryUtil.memPutFloat(pointer + 12, alpha);
            MemoryUtil.memPutInt(pointer + 16, overlayX);
            MemoryUtil.memPutInt(pointer + 20, overlayY);
            MemoryUtil.memPutInt(pointer + 24, lightX);
            MemoryUtil.memPutInt(pointer + 28, lightY);
            // if this overflows, we have to change it to an u64 in the shader. also, figure out how to actually calculate this as an uint.
            MemoryUtil.memPutInt(pointer + 44, (int) partArrayIndex);
        }

    }

}
