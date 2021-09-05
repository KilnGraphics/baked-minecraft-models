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

import com.oroarmor.bakedminecraftmodels.model.GlobalModelUtils;
import com.oroarmor.bakedminecraftmodels.ssbo.SectionedPbo;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.system.MemoryUtil;

public class ModelInstanceData {

    private final MatrixList modelViewMatrixList;
    private Matrix4f baseModelViewMatrix;
    private boolean colorSet;
    private float red;
    private float green;
    private float blue;
    private float alpha;
    private boolean overlaySet;
    private int overlayX;
    private int overlayY;
    private boolean lightSet;
    private int lightX;
    private int lightY;

    public ModelInstanceData() {
        this.modelViewMatrixList = new MatrixList();
    }

    public MatrixList getMatrixList() {
        return modelViewMatrixList;
    }

    /**
     * After this is set once, until it's reset, this will ignore any subsequent
     * calls to this method.
     */
    public void setColor(float red, float green, float blue, float alpha) {
        if (!colorSet) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            colorSet = true;
        }
    }

    /**
     * After this is set once, until it's reset, this will ignore any subsequent
     * calls to this method.
     */
    public void setOverlay(int overlay) {
        if (!overlaySet) {
            this.overlayX = overlay & 0xFFFF;
            this.overlayY = overlay >> 16 & 0xFFFF;
            overlaySet = true;
        }
    }

    /**
     * After this is set once, until it's reset, this will ignore any subsequent
     * calls to this method.
     */
    public void setLight(int light) {
        if (!lightSet) {
            this.lightX = light & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);
            this.lightY = light >> 16 & (LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE | 0xFF0F);
            lightSet = true;
        }
    }

    public void setBaseModelViewMatrix(Matrix4f baseModelViewMatrix) {
        if (this.baseModelViewMatrix == null) {
            this.baseModelViewMatrix = baseModelViewMatrix;
        }
    }

    public void reset() {
        modelViewMatrixList.clear();
        colorSet = false;
        overlaySet = false;
        lightSet = false;
        baseModelViewMatrix = null;
    }

    public void writeToPbos(SectionedPbo modelPbo, SectionedPbo partPbo) {
        if (!colorSet) throw new IllegalStateException("Color variable not set");
        if (!overlaySet) throw new IllegalStateException("Overlay uvs variable not set");
        if (!lightSet) throw new IllegalStateException("Light uvs variable not set");

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

        int matrixCount = modelViewMatrixList.getLargestIndex() + 1;
        boolean[] indexWrittenArray = new boolean[matrixCount];
        MatrixList.Node currentNode;
        while ((currentNode = modelViewMatrixList.next()) != null) {
            int idx = currentNode.getIndex();
            indexWrittenArray[idx] = true;
            UnsafeUtil.writeMatrix4fUnsafe(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE, currentNode.getMatrix());
            //writeMatrix4f(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE, currentNode.getMatrix());
        }

        for (int idx = 0; idx < indexWrittenArray.length; idx++) {
            if (!indexWrittenArray[idx]) {
                UnsafeUtil.writeMatrix4fUnsafe(partPboPointer + idx * GlobalModelUtils.PART_STRUCT_SIZE, baseModelViewMatrix);
            }
        }
        modelViewMatrixList.reset();
        partPbo.addPositionOffset(matrixCount * GlobalModelUtils.PART_STRUCT_SIZE);
    }

    public static void writeMatrix4f(long pointer, Matrix4f matrix) {
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

}
