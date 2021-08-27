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
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.Matrix4f;

import java.nio.ByteBuffer;
import java.util.List;

public class ModelInstanceData {

    private final List<Matrix4f> modelViewMatrixList;
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
        this.modelViewMatrixList = new ObjectArrayList<>(10);
    }

    public List<Matrix4f> getMatrices() {
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
        modelPboPointer.putInt((partPboPointer.position() - (int) (partPbo.getCurrentSection() * partPbo.getSectionSize())) / GlobalModelUtils.PART_STRUCT_SIZE);

        for (Matrix4f modelViewMatrix : modelViewMatrixList) {
            if (modelViewMatrix == null) modelViewMatrix = baseModelViewMatrix;
            partPboPointer.putFloat(modelViewMatrix.a00).putFloat(modelViewMatrix.a10).putFloat(modelViewMatrix.a20).putFloat(modelViewMatrix.a30)
                    .putFloat(modelViewMatrix.a01).putFloat(modelViewMatrix.a11).putFloat(modelViewMatrix.a21).putFloat(modelViewMatrix.a31)
                    .putFloat(modelViewMatrix.a02).putFloat(modelViewMatrix.a12).putFloat(modelViewMatrix.a22).putFloat(modelViewMatrix.a32)
                    .putFloat(modelViewMatrix.a03).putFloat(modelViewMatrix.a13).putFloat(modelViewMatrix.a23).putFloat(modelViewMatrix.a33);
        }
    }

}
