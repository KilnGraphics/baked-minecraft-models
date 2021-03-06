/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.data;

import graphics.kiln.bakedminecraftmodels.model.GlobalModelUtils;
import graphics.kiln.bakedminecraftmodels.ssbo.SectionedPersistentBuffer;
import org.lwjgl.system.MemoryUtil;

public record PerInstanceData(long partArrayIndex, float red, float green, float blue, float alpha, int overlayX,
                              int overlayY, int lightX, int lightY, int[] primitiveIndices, int skippedPrimitivesStart,
                              int skippedPrimitivesEnd) {

    public void writeToBuffer(SectionedPersistentBuffer buffer) {
        long positionOffset = buffer.getPositionOffset().getAndAdd(GlobalModelUtils.MODEL_STRUCT_SIZE);
        long pointer = buffer.getSectionedPointer() + positionOffset;
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
