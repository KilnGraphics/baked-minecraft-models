/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.renderlayer;

import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.Shader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.function.Supplier;

@Mixin(RenderPhase.Shader.class)
public interface RenderPhaseShaderAccessor {
    @Accessor
    Optional<Supplier<Shader>> getSupplier();
}
