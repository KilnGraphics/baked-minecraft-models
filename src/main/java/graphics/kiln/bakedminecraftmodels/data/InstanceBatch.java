/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import graphics.kiln.bakedminecraftmodels.gl.GlSsboRenderDispacher;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import it.unimi.dsi.fastutil.ints.IntArrays;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;

public class InstanceBatch {

    private final SectionedPersistentBuffer partBuffer;
    private final List<PerInstanceData> instances;
    private final MatrixEntryList matrices;

    private int[] primitiveIndices;
    private int skippedPrimitives;

    private VertexFormat.IntType indexType;
    private long indexOffset;

    public InstanceBatch(int initialSize, SectionedPersistentBuffer partBuffer) {
        this.instances = new ArrayList<>(initialSize);
        this.matrices = new MatrixEntryList(initialSize);
        this.partBuffer = partBuffer;
    }

    public void reset() {
        instances.clear();
        matrices.clear();

        primitiveIndices = null;
        skippedPrimitives = 0;
        indexType = null;
        indexOffset = 0;
    }

    public boolean isIndexed() {
        return primitiveIndices != null;
    }

    public MatrixEntryList getMatrices() {
        return matrices;
    }

    public void writeInstancesToBuffer(SectionedPersistentBuffer buffer) {
        for (PerInstanceData perInstanceData : instances) {
            perInstanceData.writeToBuffer(buffer);
        }
    }

    public void addInstance(VboBackedModel model, RenderLayer renderLayer, MatrixStack.Entry baseMatrixEntry, float red, float green, float blue, float alpha, int overlay, int light) {
        int overlayX = overlay & 0xFFFF;
        int overlayY = overlay >> 16 & 0xFFFF;
        int lightX = light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);
        int lightY = light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);

        // this can happen if the model didn't render any modelparts,
        // in which case it makes sense to not try to render it anyway.
        if (matrices.isEmpty()) return;
        long partIndex = matrices.writeToBuffer(partBuffer, baseMatrixEntry);

        // While we have the matrices around, do the transparency processing if required
        MultiPhaseParametersAccessor multiPhaseParameters = (MultiPhaseParametersAccessor) (Object) ((MultiPhaseRenderPassAccessor) renderLayer).getPhases();
        //noinspection ConstantConditions
        if (GlSsboRenderDispacher.requiresIndexing(multiPhaseParameters)) {
            // Build the camera transforms from all the part transforms
            // This is how we quickly measure the depth of each primitive - find the
            // camera's position in model space, rather than applying the matrix multiply
            // to the primitive's position.
            int partIds = matrices.getLargestPartId() + 1;
            float[] cameraPositions = new float[partIds * 3];
            for (int partId = 0; partId < partIds; partId++) {
                Matrix4f m;
                if (!matrices.getElementWritten(partId)) {
                    m = baseMatrixEntry.getModel();
                } else {
                    MatrixStack.Entry entry = matrices.get(partId);
                    if (entry != null) {
                        m = entry.getModel();
                    } else {
                        // skip empty part
                        continue;
                    }
                }

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
            int totalPrimitives = primitivePartIds.length;

            float[] primitiveSqDistances = new float[totalPrimitives];
            int[] primitiveIndices = new int[totalPrimitives];
            for (int i = 0; i < totalPrimitives; i++) {
                primitiveIndices[i] = i;
            }
            int skippedPrimitives = 0;

            for (int prim = 0; prim < totalPrimitives; prim++) {
                // skip if written as null
                int partId = primitivePartIds[prim];
                if (matrices.getElementWritten(partId) && matrices.get(partId) == null) {
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

            // sort distances closest to furthest for front to back order
            IntArrays.quickSort(primitiveIndices, (i1, i2) -> Float.compare(primitiveSqDistances[i1], primitiveSqDistances[i2]));

            setPrimitiveIndices(primitiveIndices, skippedPrimitives);
        }

        instances.add(new PerInstanceData(partIndex, red, green, blue, alpha, overlayX, overlayY, lightX, lightY));
    }

    public int size() {
        return instances.size();
    }

    public void setPrimitiveIndices(int[] primitiveIndices, int skippedPrimitives) {
        this.primitiveIndices = primitiveIndices;
        this.skippedPrimitives = skippedPrimitives;
    }

    public void tryWriteIndicesToBuffer(VertexFormat.DrawMode drawMode, int indexCount, SectionedPersistentBuffer buffer) {
        if (!isIndexed()) return;

        indexType = VertexFormat.IntType.getSmallestTypeFor(indexCount);
        long sizeBytes = (long) indexCount * indexType.size;
        // add with alignment
        long startingPosUnaligned = buffer.getPositionOffset().getAndAccumulate(sizeBytes, (prev, add) -> alignPowerOf2(prev, indexType.size) + add);
        long startingPosAligned = alignPowerOf2(startingPosUnaligned, indexType.size);
        indexOffset = startingPosAligned / indexType.size;
        // TODO: is the sectioned pointer always aligned to what we want? does it need to be aligned?
        long ptr = buffer.getSectionedPointer() + startingPosAligned;

        IndexWriter indexWriter = getIndexFunction(indexType, drawMode);
        for (int instance = 0; instance < size(); instance++) {
            for (int i = skippedPrimitives; i < primitiveIndices.length; i++) {
                int indexStart = (instance * (primitiveIndices.length - skippedPrimitives) + primitiveIndices[i]) * drawMode.vertexCount;
                indexWriter.writeIndices(ptr, indexStart, drawMode.vertexCount);
                ptr += (long) drawMode.getSize(drawMode.vertexCount) * indexType.size;
            }
        }
    }

    // The Cool Way(tm) to do index writing
    private static IndexWriter getIndexFunction(VertexFormat.IntType indexType, VertexFormat.DrawMode drawMode) {
        IndexWriter function;
        switch (indexType) {
            case BYTE -> {
                switch (drawMode) {
                    case LINES -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutByte(ptr, (byte) startIdx);
                        MemoryUtil.memPutByte(ptr + 1, (byte) (startIdx + 1));
                        MemoryUtil.memPutByte(ptr + 2, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr + 3, (byte) (startIdx + 3));
                        MemoryUtil.memPutByte(ptr + 4, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr + 5, (byte) (startIdx + 1));
                    };
                    case QUADS -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutByte(ptr, (byte) startIdx);
                        MemoryUtil.memPutByte(ptr + 1, (byte) (startIdx + 1));
                        MemoryUtil.memPutByte(ptr + 2, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr + 3, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr + 4, (byte) (startIdx + 3));
                        MemoryUtil.memPutByte(ptr + 5, (byte) startIdx);
                    };
                    default -> function = (ptr, startIdx, vertsPerPrim) -> {
                        for (int i = 0; i < drawMode.vertexCount; i++) {
                            MemoryUtil.memPutByte(ptr + i, (byte) (startIdx + i));
                        }
                    };
                }
            }
            case SHORT -> {
                switch (drawMode) {
                    case LINES -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutShort(ptr, (short) startIdx);
                        MemoryUtil.memPutShort(ptr + 2, (short) (startIdx + 1));
                        MemoryUtil.memPutShort(ptr + 4, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr + 6, (short) (startIdx + 3));
                        MemoryUtil.memPutShort(ptr + 8, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr + 10, (short) (startIdx + 1));
                    };
                    case QUADS -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutShort(ptr, (short) startIdx);
                        MemoryUtil.memPutShort(ptr + 2, (short) (startIdx + 1));
                        MemoryUtil.memPutShort(ptr + 4, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr + 6, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr + 8, (short) (startIdx + 3));
                        MemoryUtil.memPutShort(ptr + 10, (short) startIdx);
                    };
                    default -> function = (ptr, startIdx, vertsPerPrim) -> {
                        for (int i = 0; i < drawMode.vertexCount; i++) {
                            MemoryUtil.memPutShort(ptr + i * 2L, (short) (startIdx + i));
                        }
                    };
                }
            }
            case INT -> {
                switch (drawMode) {
                    case LINES -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutInt(ptr, startIdx);
                        MemoryUtil.memPutInt(ptr + 4, startIdx + 1);
                        MemoryUtil.memPutInt(ptr + 8, startIdx + 2);
                        MemoryUtil.memPutInt(ptr + 12, startIdx + 3);
                        MemoryUtil.memPutInt(ptr + 16, startIdx + 2);
                        MemoryUtil.memPutInt(ptr + 20, startIdx + 1);
                    };
                    case QUADS -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutInt(ptr, startIdx);
                        MemoryUtil.memPutInt(ptr + 4, startIdx + 1);
                        MemoryUtil.memPutInt(ptr + 8, startIdx + 2);
                        MemoryUtil.memPutInt(ptr + 12,startIdx + 2);
                        MemoryUtil.memPutInt(ptr + 16,startIdx + 3);
                        MemoryUtil.memPutInt(ptr + 20, startIdx);
                    };
                    default -> function = (ptr, startIdx, vertsPerPrim) -> {
                        for (int i = 0; i < drawMode.vertexCount; i++) {
                            MemoryUtil.memPutInt(ptr + i * 4L, startIdx + i);
                        }
                    };
                }
            }
            default -> throw new IllegalArgumentException("Index type " + indexType.name() + " unknown");
        }
        return function;
    }

    // multiple must be a power of 2
    private static long alignPowerOf2(long numToRound, long multiple) {
        // TODO: make sure this always works
        return (numToRound + multiple - 1) & -multiple;
    }

    public long getIndexOffset() {
        return indexOffset;
    }

    public VertexFormat.IntType getIndexType() {
        return indexType;
    }

}
