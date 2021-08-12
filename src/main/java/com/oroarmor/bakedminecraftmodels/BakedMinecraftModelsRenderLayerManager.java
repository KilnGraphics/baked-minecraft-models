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

package com.oroarmor.bakedminecraftmodels;

import java.util.HashMap;
import java.util.Map;

import com.oroarmor.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.renderlayer.RenderLayerAccessor;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;

public class BakedMinecraftModelsRenderLayerManager {
    private static final Map<RenderLayer, RenderLayer> dumbToSmart = new HashMap<>();

    public static RenderLayer turnIntoSmartRenderLayer(@Nullable RenderLayer dumbRenderLayer) {
        return dumbToSmart.computeIfAbsent(dumbRenderLayer, _dumbRenderLayer -> {
            if (_dumbRenderLayer == null) {
                return null;
            }

            RenderLayerAccessor dumbMultiPhaseRenderPass = ((RenderLayerAccessor) _dumbRenderLayer);
            MultiPhaseParametersAccessor dumbMultiPhaseParameters = ((MultiPhaseParametersAccessor) (Object) ((MultiPhaseRenderPassAccessor) dumbMultiPhaseRenderPass).getPhases());

            RenderLayer.MultiPhaseParameters phaseParameters = RenderLayer.MultiPhaseParameters.builder()
                    .cull(dumbMultiPhaseParameters.getCull())
                    .depthTest(dumbMultiPhaseParameters.getDepthTest())
                    .layering(dumbMultiPhaseParameters.getLayering())
                    .lightmap(dumbMultiPhaseParameters.getLightmap())
                    .lineWidth(dumbMultiPhaseParameters.getLineWidth())
                    .overlay(dumbMultiPhaseParameters.getOverlay())
                    // TODO: actually check the renderlayer to determine the shader, not a huge issue right now, but will cause issues later
                    .shader(new RenderPhase.Shader(() -> BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL))
                    .target(dumbMultiPhaseParameters.getTarget())
                    .texture(dumbMultiPhaseParameters.getTexture())
                    .texturing(dumbMultiPhaseParameters.getTexturing())
                    .transparency(dumbMultiPhaseParameters.getTransparency())
                    .writeMaskState(dumbMultiPhaseParameters.getWriteMaskState())
                    .build(dumbMultiPhaseParameters.getOutlineMode());

            return new RenderLayer.MultiPhase(
                    dumbMultiPhaseRenderPass.getName(),
                    BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT,
                    VertexFormat.DrawMode.QUADS,
                    dumbMultiPhaseRenderPass.getExpectedBufferSize(),
                    dumbMultiPhaseRenderPass.getHasCrumbling(),
                    dumbMultiPhaseRenderPass.getTranslucent(),
                    phaseParameters
            );
        });
    }
}
