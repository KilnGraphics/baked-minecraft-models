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
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;
import org.lwjgl.system.MemoryUtil;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class BakingData implements Closeable, Iterable<Map<RenderLayer, Map<VboBackedModel, List<BakingData.PerInstanceData>>>> {

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

        // While we have the matrices around, do the transparency processing if required
        ByteBuffer ebo;
        VertexFormat.IntType eboType;
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

                // This doesn't account for scaling:
                cameraPositions[i] = new Vec3f(
                        -(m.a00 * m.a03 + m.a10 * m.a13 + m.a20 * m.a23) * undoScaleX * undoScaleX,
                        -(m.a01 * m.a03 + m.a11 * m.a13 + m.a21 * m.a23) * undoScaleY * undoScaleY,
                        -(m.a02 * m.a03 + m.a12 * m.a13 + m.a22 * m.a23) * undoScaleZ * undoScaleZ
                );
            }

            // Sort the verts by depth
            // TODO reduce allocations
            // TODO profile and optimise all this
            // TODO write directly to the SSBO here
            VboBackedModel.BakedIndexMetadata meta = model.getBakedIndexMetadata();
            ByteBuffer bakedIndexData = model.getBakedIndexData();
            int[] primitiveIndexes = new int[meta.primitiveCount()];
            float[] primitiveDistances = new float[meta.primitiveCount()];
            int centrePosIdx = meta.positionsOffset();
            for (int i = 0; i < primitiveIndexes.length; i++) {
                primitiveIndexes[i] = i;

                // Read out the vertex centre position
                float x = bakedIndexData.getFloat(centrePosIdx);
                centrePosIdx += 4;
                float y = bakedIndexData.getFloat(centrePosIdx);
                centrePosIdx += 4;
                float z = bakedIndexData.getFloat(centrePosIdx);
                centrePosIdx += 4;

                // FIXME use the correct part matrix
                Vec3f cameraPos = cameraPositions[0];
                float deltaX = x - cameraPos.getX();
                float deltaY = y - cameraPos.getY();
                float deltaZ = z - cameraPos.getZ();

                float distSq = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                primitiveDistances[i] = distSq;
            }
            IntArrays.mergeSort(primitiveIndexes, (a, b) -> (int) Math.signum(primitiveDistances[a] - primitiveDistances[b]));

            ebo = ByteBuffer.allocate(meta.indexDataSize());
            ebo.order(ByteOrder.nativeOrder());
            eboType = meta.indexFormat();
            int stride = meta.indexStride();
            for (int i = 0; i < primitiveIndexes.length; i++) {
                int pos = stride * primitiveIndexes[i];
                ebo.put(i * stride, bakedIndexData, pos, stride);
            }
        } else {
            ebo = null;
            eboType = null;
        }

        matrixEntryList.clear();
        matrixEntryListPool.offerLast(matrixEntryList);

        // TODO: make this whole thing concurrent if it needs to be
        renderSection
                .computeIfAbsent(renderLayer, unused -> new LinkedHashMap<>())
                .computeIfAbsent(model, unused -> new LinkedList<>()) // we use a LinkedList here because ArrayList takes a long time to grow
                .add(new BakingData.PerInstanceData(futurePartIndex, red, green, blue, alpha, overlayX, overlayY, lightX, lightY, ebo, eboType));
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

    public Iterator<Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>>> iterator() {
        return Iterators.concat(Iterators.singletonIterator(opaqueSection), orderedTransparencySections.iterator());
    }

    public boolean isEmptyShallow() {
        return opaqueSection.isEmpty() && orderedTransparencySections.isEmpty();
    }

    public boolean isEmptyDeep() {
        for (Map<RenderLayer, Map<VboBackedModel, List<PerInstanceData>>> perOrderedSectionData : this) {
            for (Map<VboBackedModel, List<PerInstanceData>> perRenderLayerData : perOrderedSectionData.values()) {
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
        closeables.clear();
    }

    public record PerInstanceData(long partArrayIndex, float red, float green, float blue, float alpha, int overlayX,
                                  int overlayY, int lightX, int lightY,
                                  // The EBO contents. Formatted as {a,b,c,float-as-int-distance-to-cam} tuples
                                  // TODO convert to a @param comment
                                  ByteBuffer eboData, VertexFormat.IntType eboType
    ) {

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
