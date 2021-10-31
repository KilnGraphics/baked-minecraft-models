/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import com.google.common.collect.Iterators;
import graphics.kiln.bakedminecraftmodels.BakedMinecraftModels;
import graphics.kiln.bakedminecraftmodels.gl.GlSsboRenderDispacher;
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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;
import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.util.*;

public class BakingData implements Closeable, Iterable<Map<RenderLayer, Map<VboBackedModel, InstanceBatch>>> {

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
    private final Deque<MatrixEntryList> matrixEntryListPool;

    private RenderPhase.Transparency previousTransparency;

    public BakingData(SectionedPersistentBuffer modelPersistentSsbo, SectionedPersistentBuffer partPersistentSsbo, SectionedPersistentBuffer translucencyPersistentEbo) {
        this.modelPersistentSsbo = modelPersistentSsbo;
        this.partPersistentSsbo = partPersistentSsbo;
        this.translucencyPersistentEbo = translucencyPersistentEbo;
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

        // While we have the matrices around, do the transparency processing if required
        if (GlSsboRenderDispacher.isTransparencySorted(currentTransparency)) {
            // Build the camera transforms from all the part transforms
            // This is how we quickly measure the depth of each primitive - find the
            // camera's position in model space, rather than applying the matrix multiply
            // to the primitive's position.
            Vec3f[] cameraPositions = new Vec3f[matrixEntryList.getLargestPartId()];
            for (int i = 0; i < cameraPositions.length; i++) {
                Matrix4f m = matrixEntryList.getElementModelTransform(i);

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

                cameraPositions[i] = new Vec3f(
                        -(m.a00 * m.a03 + m.a10 * m.a13 + m.a20 * m.a23) * undoScaleX * undoScaleX,
                        -(m.a01 * m.a03 + m.a11 * m.a13 + m.a21 * m.a23) * undoScaleY * undoScaleY,
                        -(m.a02 * m.a03 + m.a12 * m.a13 + m.a22 * m.a23) * undoScaleZ * undoScaleZ
                );
            }

            float[] vertexPositions = model.getVertexPositions();
            int[] primitivePartIds = model.getPrimitivePartIds();

            int vertsPerPrimitive = renderLayer.getDrawMode().vertexCount;
            int totalPrimitives = vertexPositions.length / vertsPerPrimitive;
            float[] primitiveSqDistances = new float[totalPrimitives];
            int[] primitiveIndices = new int[totalPrimitives];
            for (int i = 0; i < totalPrimitives; i++) {
                primitiveIndices[i] = i;
            }
            int skippedPrimitives = 0;

            for (int prim = 0; prim < totalPrimitives; prim++) {
                // skip if not written
                int partId = primitivePartIds[prim];
                if (!matrices.getElementWritten(partId)) {
                    primitiveSqDistances[prim] = Float.MIN_VALUE;
                    skippedPrimitives++;
                }

                // average vertex positions in primitive
                float totalX = 0;
                float totalY = 0;
                float totalZ = 0;
                for (int vert = 0; vert < vertsPerPrimitive; vert++) {
                    // positions per previous primitives plus positions per previous points (vertices)
                    // bars
                    int startingPos = prim * vertsPerPrimitive * 3 + vert * 3;
                    totalX += vertexPositions[startingPos + vert];
                    totalY += vertexPositions[startingPos + vert + 1];
                    totalZ += vertexPositions[startingPos + vert + 2];
                }
                float x = totalX / vertsPerPrimitive;
                float y = totalY / vertsPerPrimitive;
                float z = totalZ / vertsPerPrimitive;

                Matrix4f modelView = matrices.get(partId).getModel();
                // subtract translation components of matrix to get delta
                float deltaX = x - modelView.a00;
                float deltaY = y - modelView.a01;
                float deltaZ = z - modelView.a02;
                primitiveSqDistances[prim] = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
            }

            IntArrays.quickSort(primitiveIndices, (i1, i2) -> Float.compare(primitiveSqDistances[i1], primitiveSqDistances[i2]));



        }

        long futurePartIndex = matrixEntryList.writeToBuffer(partPersistentSsbo, baseMatrixEntry);
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
                    perInstanceData.writeToBuffer(modelPersistentSsbo);
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

    public boolean isEmptyDeep() {
        for (Map<RenderLayer, Map<VboBackedModel, InstanceBatch>> perOrderedSectionData : this) {
            for (Map<VboBackedModel, InstanceBatch> perRenderLayerData : perOrderedSectionData.values()) {
                for (List<InstanceBatch> perModelData : perRenderLayerData.values()) {
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
