/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels;

import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseParametersAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.MultiPhaseRenderPassAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.RenderLayerAccessor;
import graphics.kiln.bakedminecraftmodels.mixin.renderlayer.RenderPhaseShaderAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.render.*;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class BakedMinecraftModelsRenderLayerManager {
    private static final Map<RenderLayer, RenderLayer> dumbToSmart = new Object2ObjectOpenHashMap<>();
    private static Map<Shader, Shader> SHADER_CONVERSION_MAP;

    @SuppressWarnings("ConstantConditions")
    public static RenderLayer tryDeriveSmartRenderLayer(@Nullable RenderLayer dumbRenderLayer) {
        if (dumbRenderLayer == null) {
            return null;
        }

        MultiPhaseParametersAccessor dumbMultiPhaseParameters;
        if (dumbRenderLayer instanceof MultiPhaseRenderPassAccessor dumbMultiPhaseRenderPass) {
            dumbMultiPhaseParameters = ((MultiPhaseParametersAccessor) (Object) dumbMultiPhaseRenderPass.getPhases());
        } else {
            return null;
        }

        Optional<Supplier<Shader>> possibleSupplier = ((RenderPhaseShaderAccessor) dumbMultiPhaseParameters.getShader()).getSupplier();
        Shader dumbShader;
        if (possibleSupplier.isPresent()) {
            dumbShader = possibleSupplier.get().get();
            if (dumbShader == null) {
                return null;
            }
        } else {
            return null;
        }

        if (SHADER_CONVERSION_MAP == null) {
            SHADER_CONVERSION_MAP = Map.of(
                    GameRenderer.getRenderTypeEntityCutoutNoNullShader(), BakedMinecraftModelsShaderManager.SMART_ENTITY_CUTOUT_NO_CULL
            );
        }

        Shader convertedShader = SHADER_CONVERSION_MAP.get(dumbShader);
        if (convertedShader == null) {
            return null;
        }

        return dumbToSmart.computeIfAbsent(dumbRenderLayer, _dumbRenderLayer -> {

            RenderLayerAccessor dumbRenderLayerAccessor = ((RenderLayerAccessor) _dumbRenderLayer);

            RenderLayer.MultiPhaseParameters phaseParameters = RenderLayer.MultiPhaseParameters.builder()
                    .cull(dumbMultiPhaseParameters.getCull())
                    .depthTest(dumbMultiPhaseParameters.getDepthTest())
                    .layering(dumbMultiPhaseParameters.getLayering())
                    .lightmap(dumbMultiPhaseParameters.getLightmap())
                    .lineWidth(dumbMultiPhaseParameters.getLineWidth())
                    .overlay(dumbMultiPhaseParameters.getOverlay())
                    // TODO: actually check the renderlayer to determine the shader, not a huge issue right now, but will cause issues later
                    .shader(new RenderPhase.Shader(() -> convertedShader))
                    .target(dumbMultiPhaseParameters.getTarget())
                    .texture(dumbMultiPhaseParameters.getTexture())
                    .texturing(dumbMultiPhaseParameters.getTexturing())
                    .transparency(dumbMultiPhaseParameters.getTransparency())
                    .writeMaskState(dumbMultiPhaseParameters.getWriteMaskState())
                    .build(dumbMultiPhaseParameters.getOutlineMode());

            return new RenderLayer.MultiPhase(
                    dumbRenderLayerAccessor.getName(),
                    convertedShader.getFormat(),
                    _dumbRenderLayer.getDrawMode(),
                    _dumbRenderLayer.getExpectedBufferSize(),
                    dumbRenderLayerAccessor.getHasCrumbling(),
                    dumbRenderLayerAccessor.getTranslucent(),
                    phaseParameters
            ) {
                /**
                 * Minecraft spends a long time with the startDrawing and endDrawing setting opengl variables and such.
                 * We don't want that because we know the vertex count will always be 0.
                 */
                @Override
                public void draw(BufferBuilder buffer, int cameraX, int cameraY, int cameraZ) {
                    if (buffer.isBuilding()) {
                        if (((RenderLayerAccessor) (Object) this).getTranslucent()) {
                            buffer.setCameraPosition((float)cameraX, (float)cameraY, (float)cameraZ);
                        }

                        buffer.end();
                    }
                }
            };
        });
    }
}
