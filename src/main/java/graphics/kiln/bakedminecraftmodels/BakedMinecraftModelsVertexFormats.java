/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels;

import com.google.common.collect.ImmutableMap;

import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;

public class BakedMinecraftModelsVertexFormats {
    public static final VertexFormat SMART_ENTITY_FORMAT = new VertexFormat(
            ImmutableMap.<String, VertexFormatElement>builder()
                    .put("Position", VertexFormats.POSITION_ELEMENT)
                    .put("UV0", VertexFormats.TEXTURE_0_ELEMENT)
                    .put("Normal", VertexFormats.NORMAL_ELEMENT)
//                    .put("RawNormal", new VertexFormatElement(0, VertexFormatElement.DataType.FLOAT, VertexFormatElement.Type.GENERIC, 3))
                    .put("Padding", VertexFormats.PADDING_ELEMENT)
                    .put("PartId", new VertexFormatElement(0, VertexFormatElement.DataType.UINT, VertexFormatElement.Type.UV, 1))
                    .build());
}
