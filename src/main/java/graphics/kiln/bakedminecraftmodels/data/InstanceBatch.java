/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import net.minecraft.client.render.VertexFormat;
import org.lwjgl.system.MemoryUtil;

import java.util.ArrayList;
import java.util.List;

public class InstanceBatch {

    private final List<BakingData.PerInstanceData> instances;
    private final MatrixEntryList matrices;

    private int[] primitiveIndices;
    private int skippedPrimitives;

    private VertexFormat.IntType indexType;
    private long indexOffset;
    private int indexCount;

    public InstanceBatch(int initialSize) {
        this.instances = new ArrayList<>(initialSize);
        this.matrices = new MatrixEntryList(initialSize);
    }

    public void reset() {
        instances.clear();
        matrices.clear();

        primitiveIndices = null;
        skippedPrimitives = 0;
        indexType = null;
        indexOffset = 0;
        indexCount = 0;
    }

    public boolean isIndexed() {
        return primitiveIndices != null;
    }

    public MatrixEntryList getMatrices() {
        return matrices;
    }

    public void writeInstancesToBuffer(SectionedPersistentBuffer buffer) {
        for (BakingData.PerInstanceData perInstanceData : instances) {
            perInstanceData.writeToBuffer(buffer);
        }
    }

    public void addInstance(BakingData.PerInstanceData instanceData) {
        instances.add(instanceData);
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
        // TODO: is the sectioned pointer always aligned to what we want? does it need to be aligned?
        long ptr = buffer.getSectionedPointer() + alignPowerOf2(startingPosUnaligned, indexType.size);
        indexOffset = ptr / indexType.size;

        IndexWriter indexWriter = getIndexFunction(indexType, drawMode);
        for (int instance = 0; instance < size(); instance++) {
            for (int i = skippedPrimitives; i < primitiveIndices.length; i++) {
                int indexStart = primitiveIndices[i] * drawMode.vertexCount;
                indexWriter.writeIndices(ptr, indexStart, drawMode.vertexCount);
                ptr += drawMode.vertexCount;
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
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 1));
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 3));
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 1));
                    };
                    case QUADS -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutByte(ptr, (byte) startIdx);
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 1));
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 2));
                        MemoryUtil.memPutByte(ptr, (byte) (startIdx + 3));
                        MemoryUtil.memPutByte(ptr, (byte) startIdx);
                    };
                    default -> function = (ptr, startIdx, vertsPerPrim) -> {
                        for (int i = 0; i < drawMode.vertexCount; i++) {
                            MemoryUtil.memPutByte(ptr, (byte) (startIdx + i));
                        }
                    };
                }
            }
            case SHORT -> {
                switch (drawMode) {
                    case LINES -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutShort(ptr, (short) startIdx);
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 1));
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 3));
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 1));
                    };
                    case QUADS -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutShort(ptr, (short) startIdx);
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 1));
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 2));
                        MemoryUtil.memPutShort(ptr, (short) (startIdx + 3));
                        MemoryUtil.memPutShort(ptr, (short) startIdx);
                    };
                    default -> function = (ptr, startIdx, vertsPerPrim) -> {
                        for (int i = 0; i < drawMode.vertexCount; i++) {
                            MemoryUtil.memPutShort(ptr, (short) (startIdx + i));
                        }
                    };
                }
            }
            case INT -> {
                switch (drawMode) {
                    case LINES -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutInt(ptr, startIdx);
                        MemoryUtil.memPutInt(ptr, startIdx + 1);
                        MemoryUtil.memPutInt(ptr, startIdx + 2);
                        MemoryUtil.memPutInt(ptr, startIdx + 3);
                        MemoryUtil.memPutInt(ptr, startIdx + 2);
                        MemoryUtil.memPutInt(ptr, startIdx + 1);
                    };
                    case QUADS -> function = (ptr, startIdx, ignored) -> {
                        MemoryUtil.memPutInt(ptr, startIdx);
                        MemoryUtil.memPutInt(ptr, startIdx + 1);
                        MemoryUtil.memPutInt(ptr, startIdx + 2);
                        MemoryUtil.memPutInt(ptr, startIdx + 2);
                        MemoryUtil.memPutInt(ptr, startIdx + 3);
                        MemoryUtil.memPutInt(ptr, startIdx);
                    };
                    default -> function = (ptr, startIdx, vertsPerPrim) -> {
                        for (int i = 0; i < drawMode.vertexCount; i++) {
                            MemoryUtil.memPutInt(ptr, startIdx + i);
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

    public long getIndexCount() {
        return indexCount;
    }

    public VertexFormat.IntType getIndexType() {
        return indexType;
    }

}
