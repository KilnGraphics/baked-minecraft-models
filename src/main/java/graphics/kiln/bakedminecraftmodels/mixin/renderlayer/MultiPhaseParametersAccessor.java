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
