/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.RenderLayerAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.RenderPhaseAccessor;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.*;

public class BakingData {

    /**
     * Each map in the deque represents a separate ordered section, which is required for transparency ordering.
     * For each model in the map, it has its own map where each RenderLayer has a list of instances. This is
     * because we can only batch instances with the same RenderLayer and model.
     */
    private final Deque<Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>>> internalData;

    private RenderLayer currentRenderLayer;
    private VboBackedModel currentModel;
    private Matrix4f currentBaseMatrix;
    private MatrixList stagingMatrixList;

    private RenderPhase.Transparency previousTransparency;

    public BakingData() {
        internalData = new ArrayDeque<>(256);
    }

    public void beginInstance(VboBackedModel model, RenderLayer renderLayer, Matrix4f baseMatrix) {
        currentModel = model;
        currentRenderLayer = renderLayer;
        currentBaseMatrix = baseMatrix;
        stagingMatrixList = new MatrixList();
    }

    public void endInstance(float red, float green, float blue, float alpha, int overlay, int light) {
        int overlayX = overlay & 0xFFFF;
        int overlayY = overlay >> 16 & 0xFFFF;
        int lightX = light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);
        int lightY = light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);

        // we still use linked maps in here to try to preserve patterns for things that rely on rendering in order not based on transparency.
        RenderPhase.Transparency currentTransparency = ((MultiPhaseParametersAccessor)(Object)(((MultiPhaseRenderPassAccessor) currentRenderLayer).getPhases())).getTransparency();
        if (internalData.size() == 0) {
            internalData.add(new LinkedHashMap<>());
        } else if (currentTransparency instanceof RenderPhaseAccessor currentTransparencyAccessor && previousTransparency instanceof RenderPhaseAccessor previousTransparencyAccessor) {
            String currentTransparencyName = currentTransparencyAccessor.getName();
            String previousTransparencyName = previousTransparencyAccessor.getName();
            // additive can be unordered and still have the correct output
            if (!currentTransparencyName.equals("no_transparency") && !(currentTransparencyName.equals("additive_transparency") && previousTransparencyName.equals("additive_transparency"))) {
                internalData.add(new LinkedHashMap<>());
            }
        }
        previousTransparency = currentTransparency;

        internalData.peek()
                .computeIfAbsent(currentRenderLayer, unused -> new LinkedHashMap<>())
                .computeIfAbsent(currentModel, unused -> new LinkedList<>()) // we use a LinkedList here because ArrayList takes a long time to grow
                .add(new BakingData.PerInstanceData(currentBaseMatrix, stagingMatrixList, red, green, blue, alpha, overlayX, overlayY, lightX, lightY));
    }

    public void addPartMatrix(int index, Matrix4f partMatrix) {
        stagingMatrixList.add(index, partMatrix);
    }

    public void writeToBuffer(SectionedPersistentBuffer modelPbo, SectionedPersistentBuffer partPbo) {
        for (Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> perOrderedSectionData : internalData) {
            for (Map<VboBackedModel, List<PerInstanceData>> perRenderLayerData : perOrderedSectionData.values()) {
                for (List<PerInstanceData> perModelData : perRenderLayerData.values()) {
                    for (PerInstanceData perInstanceData : perModelData) {
                        perInstanceData.writeToBuffer(modelPbo, partPbo);
                    }
                }
            }
        }
    }

    public Deque<Map<RenderLayer, Map<VboBackedModel, List<?>>>> getInternalData() {
        return (Deque<Map<RenderLayer, Map<VboBackedModel, List<?>>>>) (Object) internalData;
    }

    public boolean isEmptyShallow() {
        return internalData.isEmpty();
    }

    public boolean isEmptyDeep() {
        for (Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> perOrderedSectionData : internalData) {
            for (Map<VboBackedModel, List<PerInstanceData>> perRenderLayerData : perOrderedSectionData.values()) {
                for (List<PerInstanceData> perModelData : perRenderLayerData.values()) {
                    if (perModelData.size() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void reset() {
        internalData.clear();
    }

    private record PerInstanceData(Matrix4f baseMatrix, MatrixList partMatrices, float red, float green, float blue, float alpha, int overlayX, int overlayY, int lightX, int lightY) {

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
            MatrixList.Node currentNode;
            while ((currentNode = partMatrices.next()) != null) {
                int idx = currentNode.getIndex();
                indexWrittenArray[idx] = true;
                Matrix4f matrix = currentNode.getMatrix();
                if (matrix != null) {
                    writeMatrix4f(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE, matrix);
                } else {
                    writeNullMatrix4f(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE);
                }
            }

            for (int idx = 0; idx < indexWrittenArray.length; idx++) {
                if (!indexWrittenArray[idx]) {
                    writeMatrix4f(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE, baseMatrix);
                }
            }
            partMatrices.reset();
            partPbo.addPositionOffset(matrixCount * GlobalModelUtils.PART_STRUCT_SIZE);
        }

        private static void writeMatrix4f(long pointer, Matrix4f matrix) {
            MemoryUtil.memPutFloat(pointer, matrix.a00);
            MemoryUtil.memPutFloat(pointer + 4, matrix.a10);
            MemoryUtil.memPutFloat(pointer + 8, matrix.a20);
            MemoryUtil.memPutFloat(pointer + 12, matrix.a30);
            MemoryUtil.memPutFloat(pointer + 16, matrix.a01);
            MemoryUtil.memPutFloat(pointer + 20, matrix.a11);
            MemoryUtil.memPutFloat(pointer + 24, matrix.a21);
            MemoryUtil.memPutFloat(pointer + 28, matrix.a31);
            MemoryUtil.memPutFloat(pointer + 32, matrix.a02);
            MemoryUtil.memPutFloat(pointer + 36, matrix.a12);
            MemoryUtil.memPutFloat(pointer + 40, matrix.a22);
            MemoryUtil.memPutFloat(pointer + 44, matrix.a32);
            MemoryUtil.memPutFloat(pointer + 48, matrix.a03);
            MemoryUtil.memPutFloat(pointer + 52, matrix.a13);
            MemoryUtil.memPutFloat(pointer + 56, matrix.a23);
            MemoryUtil.memPutFloat(pointer + 60, matrix.a33);
        }

        private static void writeNullMatrix4f(long pointer) {
            MemoryUtil.memSet(pointer, 0, 64);
        }
    }

}
