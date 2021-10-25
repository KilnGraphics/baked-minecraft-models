/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.buffer;

import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VertexBuffer.class)
public interface VertexBufferAccessor {
    /**
     * Get the index (NOT vertex) count. This is the number of items in the EBO, not the number
     * of primitives.
     */
    @Accessor
    int getVertexCount();

    @Accessor
    VertexFormat.DrawMode getDrawMode();

    /**
     * Get the type of integer used to store the index data (NOT vertex data).
     */
    @Accessor
    VertexFormat.IntType getVertexFormat();

    @Accessor
    int getVertexBufferId();

    @Invoker
    void invokeBindVertexArray();

    @Invoker
    void invokeBind();
}
