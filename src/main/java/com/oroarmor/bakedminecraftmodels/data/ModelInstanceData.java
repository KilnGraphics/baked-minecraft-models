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

import java.nio.ByteBuffer;

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

        ByteBuffer modelPboPointer = modelPbo.getPointer();
        ByteBuffer partPboPointer = partPbo.getPointer();
        modelPboPointer.putFloat(red);
        modelPboPointer.putFloat(green);
        modelPboPointer.putFloat(blue);
        modelPboPointer.putFloat(alpha);
        modelPboPointer.putInt(overlayX);
        modelPboPointer.putInt(overlayY);
        modelPboPointer.putInt(lightX);
        modelPboPointer.putInt(lightY);
        modelPboPointer.position(modelPboPointer.position() + 12);
        modelPboPointer.putInt((partPboPointer.position() - (int) (partPbo.getCurrentSection() * partPbo.getSectionSize())) / GlobalModelUtils.PART_STRUCT_SIZE);

        int currentPos = partPboPointer.position();
        int matrixCount = modelViewMatrixList.getLargestIndex() + 1;
        boolean[] indexWrittenArray = new boolean[matrixCount];
        MatrixList.Node currentNode;
        while ((currentNode = modelViewMatrixList.next()) != null) {
            int idx = currentNode.getIndex();
            indexWrittenArray[idx] = true;
            writeMatrix4f(partPboPointer, currentNode.getMatrix(), currentPos + idx * GlobalModelUtils.PART_STRUCT_SIZE);
        }

        for (int idx = 0; idx < indexWrittenArray.length; idx++) {
            if (!indexWrittenArray[idx]) {
                writeMatrix4f(partPboPointer, baseModelViewMatrix, currentPos + idx * GlobalModelUtils.PART_STRUCT_SIZE);
            }
        }
        modelViewMatrixList.reset();
        partPboPointer.position(currentPos + matrixCount * GlobalModelUtils.PART_STRUCT_SIZE);
    }

    private void writeMatrix4f(ByteBuffer buffer, Matrix4f matrix, int position) {
        buffer.position(position);
        buffer.putFloat(matrix.a00).putFloat(matrix.a10).putFloat(matrix.a20).putFloat(matrix.a30)
                .putFloat(matrix.a01).putFloat(matrix.a11).putFloat(matrix.a21).putFloat(matrix.a31)
                .putFloat(matrix.a02).putFloat(matrix.a12).putFloat(matrix.a22).putFloat(matrix.a32)
                .putFloat(matrix.a03).putFloat(matrix.a13).putFloat(matrix.a23).putFloat(matrix.a33);
    }

}
