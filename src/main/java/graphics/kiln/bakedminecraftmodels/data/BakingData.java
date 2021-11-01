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
import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.system.MemoryUtil;

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

    public void addInstance(VboBackedModel model, RenderLayer renderLayer, InstanceBatch instanceBatch, MatrixStack.Entry baseMatrixEntry, float red, float green, float blue, float alpha, int overlay, int light) {
        int overlayX = overlay & 0xFFFF;
        int overlayY = overlay >> 16 & 0xFFFF;
        int lightX = light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);
        int lightY = light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);

        Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> renderSection;
        // we still use linked maps in here to try to preserve patterns for things that rely on rendering in order not based on transparency.
        MultiPhaseParametersAccessor multiPhaseParameters = (MultiPhaseParametersAccessor) (Object) ((MultiPhaseRenderPassAccessor) renderLayer).getPhases();
        @SuppressWarnings("ConstantConditions")
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

        MatrixEntryList matrixEntryList = instanceBatch.getMatrices();
        // this can happen if the model didn't render any modelparts,
        // in which case it makes sense to not try to render it anyway.
        if (matrixEntryList == null) return;
        long partIndex = matrixEntryList.writeToBuffer(partPersistentSsbo, baseMatrixEntry);

        // While we have the matrices around, do the transparency processing if required
        if (GlSsboRenderDispacher.requiresIndexing(multiPhaseParameters)) {
            // Build the camera transforms from all the part transforms
            // This is how we quickly measure the depth of each primitive - find the
            // camera's position in model space, rather than applying the matrix multiply
            // to the primitive's position.
            float[] cameraPositions = new float[matrixEntryList.getLargestPartId() * 3];
            for (int partId = 0; partId < cameraPositions.length; partId++) {
                // skip empty part
                if (!matrixEntryList.getElementWritten(partId)) continue;
                Matrix4f m = matrixEntryList.get(partId).getModel();

                // The translation of the inverse of a transform matrix is the negation of
                // the transposed rotation times the transform of the original matrix.
                //
                // The above only works if there's no scaling though - to correct for that, we
                // can find the length of each column is the scaling factor for x, y or z depending
                // on the column number. We then divide each of the output components by the
                // square of the scaling factor - since we're multiplying the scaling factor in a
                // second time with the matrix multiply, we have to divide twice (same as divide by sq root)
                // to get the actual inverse.

                // Using fastInverseSqrt might be playing with fire here
                float undoScaleX = 1 / MathHelper.sqrt(m.a00 * m.a00 + m.a10 * m.a10 + m.a20 * m.a20);
                float undoScaleY = 1 / MathHelper.sqrt(m.a01 * m.a01 + m.a11 * m.a11 + m.a21 * m.a21);
                float undoScaleZ = 1 / MathHelper.sqrt(m.a02 * m.a02 + m.a12 * m.a12 + m.a22 * m.a22);

                int arrayIdx = partId * 3;
                cameraPositions[arrayIdx] = -(m.a00 * m.a03 + m.a10 * m.a13 + m.a20 * m.a23) * undoScaleX * undoScaleX;
                cameraPositions[arrayIdx + 1] = -(m.a01 * m.a03 + m.a11 * m.a13 + m.a21 * m.a23) * undoScaleY * undoScaleY;
                cameraPositions[arrayIdx + 2] = -(m.a02 * m.a03 + m.a12 * m.a13 + m.a22 * m.a23) * undoScaleZ * undoScaleZ;
            }

            float[] primitivePositions = model.getPrimitivePositions();
            int[] primitivePartIds = model.getPrimitivePartIds();

            float[] primitiveSqDistances = new float[primitivePositions.length];
            int[] primitiveIndices = new int[primitivePositions.length];
            for (int i = 0; i < primitivePositions.length; i++) {
                primitiveIndices[i] = i;
            }
            int skippedPrimitives = 0;

            for (int prim = 0; prim < primitivePositions.length; prim++) {
                // skip if not written
                int partId = primitivePartIds[prim];
                if (!matrixEntryList.getElementWritten(partId)) {
                    primitiveSqDistances[prim] = Float.MIN_VALUE;
                    skippedPrimitives++;
                }

                int primPosIdx = prim * 3;
                float x = primitivePositions[primPosIdx];
                float y = primitivePositions[primPosIdx + 1];
                float z = primitivePositions[primPosIdx + 2];

                int camPosIdx = partId * 3;
                float deltaX = x - cameraPositions[camPosIdx];
                float deltaY = y - cameraPositions[camPosIdx + 1];
                float deltaZ = z - cameraPositions[camPosIdx + 2];
                primitiveSqDistances[prim] = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
            }

            IntArrays.quickSort(primitiveIndices, (i1, i2) -> Float.compare(primitiveSqDistances[i1], primitiveSqDistances[i2]));

            instanceBatch.setPrimitiveIndices(primitiveIndices, skippedPrimitives);
        }

        instanceBatch.addInstance(new BakingData.PerInstanceData(partIndex, red, green, blue, alpha, overlayX, overlayY, lightX, lightY));
        // this will never be null, despite what intellij thinks
        //noinspection ConstantConditions
        renderSection.computeIfAbsent(renderLayer, unused -> new LinkedHashMap<>()).put(model, instanceBatch);
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

                VertexFormat.DrawMode drawMode = vertexBufferAccessor.getDrawMode();
                int indexCount = vertexBufferAccessor.getVertexCount();
                instanceBatch.tryWriteIndicesToBuffer(drawMode, indexCount, translucencyPersistentEbo);
            }
        }
    }

    public void writeData() {
        writeSplitData(opaqueSection);

        for (Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> transparencySection : orderedTransparencySections) {
            writeSplitData(transparencySection);
        }
    }

    public InstanceBatch getOrCreateInstanceBatch() {
        return Objects.requireNonNullElseGet(batchPool.pollFirst(), () -> new InstanceBatch(INITIAL_BATCH_CAPACITY));
    }

    public void recycleInstanceBatch(InstanceBatch instanceBatch) {
        instanceBatch.reset();
        batchPool.add(instanceBatch);
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

    public record PerInstanceData(long partArrayIndex, float red, float green, float blue, float alpha, int overlayX,
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
