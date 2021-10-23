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
    @Accessor
    int getVertexCount();

    @Accessor
    VertexFormat.DrawMode getDrawMode();

    @Accessor
    VertexFormat.IntType getVertexFormat();

    @Accessor
    int getVertexBufferId();

    @Invoker
    void invokeBindVertexArray();

    @Invoker
    void invokeBind();
}
