/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.debug;

import graphics.kiln.bakedminecraftmodels.gl.GlSsboRenderDispacher;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.text.DecimalFormat;
import java.util.Map;

public class DebugInfo {
    public static final Map<String, ModelDebugInfo> modelToDebugInfoMap = new Object2ObjectOpenHashMap<>();

    public static final DecimalFormat sizeFormatter = new DecimalFormat("0.00");
    public static final String PART_BUFFER_SUFFIX = " / " + getSizeReadable(GlSsboRenderDispacher.PART_PBO_SIZE) + " (x" + GlSsboRenderDispacher.BUFFER_SECTIONS + " Buffered)";
    public static final String MODEL_BUFFER_SUFFIX = " / " + getSizeReadable(GlSsboRenderDispacher.MODEL_PBO_SIZE) + " (x" + GlSsboRenderDispacher.BUFFER_SECTIONS + " Buffered)";
    public static final String TRANSLUCENCY_EBO_SUFFIX = " (x" + GlSsboRenderDispacher.BUFFER_SECTIONS + " Buffered)";
    public static long currentPartBufferSize;
    public static long currentModelBufferSize;
    public static long currentTranslucencyEboSize;

    public static String getSizeReadable(long bytes) {
        String suffix;
        double readable;

        if (bytes >= 0x1000000000000000L) {
            suffix = " EiB";
            readable = (bytes >> 50);
        } else if (bytes >= 0x4000000000000L) {
            suffix = " PiB";
            readable = (bytes >> 40);
        } else if (bytes >= 0x10000000000L) {
            suffix = " TiB";
            readable = (bytes >> 30);
        } else if (bytes >= 0x40000000L) {
            suffix = " GiB";
            readable = (bytes >> 20);
        } else if (bytes >= 0x100000L) {
            suffix = " MiB";
            readable = (bytes >> 10);
        } else if (bytes >= 0x400L) {
            suffix = " KiB";
            readable = bytes;
        } else {
            return bytes + " B";
        }
        readable = (readable / 1024);
        return sizeFormatter.format(readable) + suffix;
    }

    public static class ModelDebugInfo {
        public int instances;
        public int sets;
    }
}
