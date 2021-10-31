/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.model;

import graphics.kiln.bakedminecraftmodels.data.BakingData;
import graphics.kiln.bakedminecraftmodels.gl.GlSsboRenderDispacher;
import graphics.kiln.bakedminecraftmodels.mixin.buffer.SpriteTexturedVertexConsumerAccessor;
import graphics.kiln.bakedminecraftmodels.vertex.SmartBufferBuilderWrapper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

public class GlobalModelUtils {

    public static final long MODEL_STRUCT_SIZE = (4 * Float.BYTES) + (2 * Integer.BYTES) + (2 * Integer.BYTES) + (3 * Float.BYTES) + Integer.BYTES;
    public static final long PART_STRUCT_SIZE = (16 * Float.BYTES) + (12 * Float.BYTES);

    public static final MatrixStack.Entry IDENTITY_STACK_ENTRY = new MatrixStack().peek();
    // FIXME: not thread safe, but making one per instance is slow
    public static final SmartBufferBuilderWrapper VBO_BUFFER_BUILDER = new SmartBufferBuilderWrapper(new BufferBuilder(32768)); // just some random initial capacity lol
    public static final GlSsboRenderDispacher INSTANCED_RENDER_DISPATCHER = new GlSsboRenderDispacher();
    public static final BakingData bakingData = new BakingData(INSTANCED_RENDER_DISPATCHER.modelPersistentSsbo, INSTANCED_RENDER_DISPATCHER.partPersistentSsbo);

    public static VertexConsumer getNestedBufferBuilder(VertexConsumer consumer) { // TODO: add more possibilities with this method, ex outline consumers
        return consumer instanceof SpriteTexturedVertexConsumer ?
                (BufferBuilder) ((SpriteTexturedVertexConsumerAccessor) consumer).getParent() :
                consumer;
    }

}
