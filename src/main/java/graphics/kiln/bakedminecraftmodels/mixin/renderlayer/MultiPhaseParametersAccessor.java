/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.mixin.renderlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;

@Mixin(RenderLayer.MultiPhaseParameters.class)
public interface MultiPhaseParametersAccessor {
    @Accessor
    RenderPhase.TextureBase getTexture();

    @Accessor
    RenderPhase.Transparency getTransparency();

    @Accessor
    RenderPhase.DepthTest getDepthTest();

    @Accessor
    RenderPhase.Cull getCull();

    @Accessor
    RenderPhase.Lightmap getLightmap();

    @Accessor
    RenderPhase.Overlay getOverlay();

    @Accessor
    RenderPhase.Layering getLayering();

    @Accessor
    RenderPhase.Target getTarget();

    @Accessor
    RenderPhase.Texturing getTexturing();

    @Accessor
    RenderPhase.WriteMaskState getWriteMaskState();

    @Accessor
    RenderPhase.LineWidth getLineWidth();

    @Accessor
    RenderLayer.OutlineMode getOutlineMode();

    @Accessor
    RenderPhase.Shader getShader();
}
