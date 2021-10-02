/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import com.google.common.collect.Iterators;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.RenderPhaseAccessor;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Matrix3f;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    private final ExecutorService uploaderService;
    private final SectionedPersistentBuffer modelPbo;
    private final SectionedPersistentBuffer partPbo;

    private RenderLayer currentRenderLayer;
    private VboBackedModel currentModel;
    private MatrixStack.Entry currentBaseMatrixEntry;
    private MatrixEntryList stagingMatrixEntryList;

    private RenderPhase.Transparency previousTransparency;

    public BakingData(SectionedPersistentBuffer modelPbo, SectionedPersistentBuffer partPbo) {
        this.modelPbo = modelPbo;
        this.partPbo = partPbo;
        opaqueSection = new LinkedHashMap<>();
        orderedTransparencySections = new ArrayDeque<>(256);
        uploaderService = Executors.newSingleThreadExecutor(r -> new Thread(r, "BakingDataUploader"));
    }

    public void beginInstance(VboBackedModel model, RenderLayer renderLayer, MatrixStack.Entry baseMatrixEntry) {
        currentModel = model;
        currentRenderLayer = renderLayer;
        currentBaseMatrixEntry = baseMatrixEntry;
        stagingMatrixEntryList = new MatrixEntryList();
    }

    public void endInstance(float red, float green, float blue, float alpha, int overlay, int light) {
        int overlayX = overlay & 0xFFFF;
        int overlayY = overlay >> 16 & 0xFFFF;
        int lightX = light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);
        int lightY = light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);

        Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> renderSection;
        // we still use linked maps in here to try to preserve patterns for things that rely on rendering in order not based on transparency.
        RenderPhase.Transparency currentTransparency = ((MultiPhaseParametersAccessor)(Object)(((MultiPhaseRenderPassAccessor) currentRenderLayer).getPhases())).getTransparency();
        if (!(currentTransparency instanceof RenderPhaseAccessor currentTransparencyAccessor) || currentTransparencyAccessor.getName().equals("no_transparency")) {
            renderSection = opaqueSection;
        } else {
            if (orderedTransparencySections.size() == 0) {
                addNewSplit();
            } else if (TRANSPARENCY_SLICING && previousTransparency instanceof RenderPhaseAccessor previousTransparencyAccessor) {
                String currentTransparencyName = currentTransparencyAccessor.getName();
                String previousTransparencyName = previousTransparencyAccessor.getName();
                // additive can be unordered and still have the correct output
                if (!(currentTransparencyName.equals("additive_transparency") && previousTransparencyName.equals("additive_transparency"))) {
                    addNewSplit();
                }
            }

            renderSection = orderedTransparencySections.peekLast();
        }
        previousTransparency = currentTransparency;

        renderSection
                .computeIfAbsent(currentRenderLayer, unused -> new LinkedHashMap<>())
                .computeIfAbsent(currentModel, unused -> new LinkedList<>()) // we use a LinkedList here because ArrayList takes a long time to grow
                .add(new BakingData.PerInstanceData(currentBaseMatrixEntry, stagingMatrixEntryList, red, green, blue, alpha, overlayX, overlayY, lightX, lightY));
    }

    private void addNewSplit() {
        orderedTransparencySections.add(new LinkedHashMap<>());
    }

    private void writeSplitData(Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> splitData) {
        for (Map<VboBackedModel, List<PerInstanceData>> perRenderLayerData : splitData.values()) {
            for (List<PerInstanceData> perModelData : perRenderLayerData.values()) {
                for (PerInstanceData perInstanceData : perModelData) {
                    perInstanceData.writeToBuffer(modelPbo, partPbo);
                }
            }
        }
    }

    public void addPartMatrix(int index, MatrixStack.Entry matrixEntry) {
        stagingMatrixEntryList.add(index, matrixEntry);
    }

    public void writeData() {
        // TODO OPT: re-make this async by returning pointers to buffer sections and making things atomic

        writeSplitData(opaqueSection);

        for (Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> transparencySection : orderedTransparencySections) {
            writeSplitData(transparencySection);
        }
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
        uploaderService.shutdownNow();
    }

    private record PerInstanceData(MatrixStack.Entry baseMatrixEntry, MatrixEntryList partMatrices, float red, float green, float blue, float alpha, int overlayX, int overlayY, int lightX, int lightY) {

        public void writeToBuffer(SectionedPersistentBuffer modelPbo, SectionedPersistentBuffer partPbo) {
            long modelPboPointer = modelPbo.getPointer();
            long partPboPointer = partPbo.getPointer();
            MemoryUtil.memPutFloat(modelPboPointer, red);
            MemoryUtil.memPutFloat(modelPboPointer + 4, green);
            MemoryUtil.memPutFloat(modelPboPointer + 8, blue);
            MemoryUtil.memPutFloat(modelPboPointer + 12, alpha);
            MemoryUtil.memPutInt(modelPboPointer + 16, overlayX);
            MemoryUtil.memPutInt(modelPboPointer + 20, overlayY);
            MemoryUtil.memPutInt(modelPboPointer + 24, lightX);
            MemoryUtil.memPutInt(modelPboPointer + 28, lightY);
            // if this overflows, we have to change it to an u64 in the shader. also, figure out how to actually calculate this as an uint.
            MemoryUtil.memPutInt(modelPboPointer + 44, (int) (partPbo.getPositionOffset() / GlobalModelUtils.PART_STRUCT_SIZE));
            modelPbo.addPositionOffset(GlobalModelUtils.MODEL_STRUCT_SIZE);

            // keep an array of written matrices. if a matrix slot in the array hasn't been written to, write the default one.
            // if the provided matrix is null, we know that it's meant to not be visible, so write a matrix of 0s.
            int matrixCount = partMatrices.getLargestIndex() + 1;
            boolean[] indexWrittenArray = new boolean[matrixCount];
            MatrixEntryList.Node currentNode;
            while ((currentNode = partMatrices.next()) != null) {
                int idx = currentNode.getIndex();
                indexWrittenArray[idx] = true;
                MatrixStack.Entry matrixEntry = currentNode.getMatrixEntry();
                if (matrixEntry != null) {
                    writeMatrixEntry(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE, matrixEntry);
                } else {
                    writeNullEntry(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE);
                }
            }

            for (int idx = 0; idx < indexWrittenArray.length; idx++) {
                if (!indexWrittenArray[idx]) {
                    writeMatrixEntry(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE, baseMatrixEntry);
                }
            }
            partMatrices.reset();
            partPbo.addPositionOffset(matrixCount * GlobalModelUtils.PART_STRUCT_SIZE);
        }

        private static void writeMatrixEntry(long pointer, MatrixStack.Entry matrixEntry) {
            Matrix4f model = matrixEntry.getModel();
            MemoryUtil.memPutFloat(pointer, model.a00);
            MemoryUtil.memPutFloat(pointer + 4, model.a10);
            MemoryUtil.memPutFloat(pointer + 8, model.a20);
            MemoryUtil.memPutFloat(pointer + 12, model.a30);
            MemoryUtil.memPutFloat(pointer + 16, model.a01);
            MemoryUtil.memPutFloat(pointer + 20, model.a11);
            MemoryUtil.memPutFloat(pointer + 24, model.a21);
            MemoryUtil.memPutFloat(pointer + 28, model.a31);
            MemoryUtil.memPutFloat(pointer + 32, model.a02);
            MemoryUtil.memPutFloat(pointer + 36, model.a12);
            MemoryUtil.memPutFloat(pointer + 40, model.a22);
            MemoryUtil.memPutFloat(pointer + 44, model.a32);
            MemoryUtil.memPutFloat(pointer + 48, model.a03);
            MemoryUtil.memPutFloat(pointer + 52, model.a13);
            MemoryUtil.memPutFloat(pointer + 56, model.a23);
            MemoryUtil.memPutFloat(pointer + 60, model.a33);

            Matrix3f normal = matrixEntry.getNormal();
            MemoryUtil.memPutFloat(pointer + 64, normal.a00);
            MemoryUtil.memPutFloat(pointer + 68, normal.a10);
            MemoryUtil.memPutFloat(pointer + 72, normal.a20);
            // padding
            MemoryUtil.memPutFloat(pointer + 80, normal.a01);
            MemoryUtil.memPutFloat(pointer + 84, normal.a11);
            MemoryUtil.memPutFloat(pointer + 88, normal.a21);
            // padding
            MemoryUtil.memPutFloat(pointer + 96, normal.a02);
            MemoryUtil.memPutFloat(pointer + 100, normal.a12);
            MemoryUtil.memPutFloat(pointer + 104, normal.a22);
            // padding
        }

        private static void writeNullEntry(long pointer) {
            MemoryUtil.memSet(pointer, 0, GlobalModelUtils.PART_STRUCT_SIZE);
        }
    }

}
