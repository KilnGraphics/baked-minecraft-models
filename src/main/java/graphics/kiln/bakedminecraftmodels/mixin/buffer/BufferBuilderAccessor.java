/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.buffer;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;

@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {

    @Accessor
    ByteBuffer getBuffer();

    @Accessor
    int getElementOffset();
}
