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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import com.oroarmor.bakedminecraftmodels.mixin.MultiPhaseParametersAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.MultiPhaseRenderPassAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.RenderLayerAccessor;
import com.oroarmor.bakedminecraftmodels.mixin.RenderPhaseAccessor;
import org.jetbrains.annotations.Nullable;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModels;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class BakedMinecraftModels implements ClientModInitializer {
    public static final String MOD_ID = "baked_minecraft_models";
    private static final boolean EXPORT_MODELS_TO_OBJ = false;

    // RenderDoc Vertex Format:
    // vec3 pos
    // rgb xbyte4 color
    // vec2 uv
    // byte3 normal
    // byte padding
    // int id

    public static final VertexFormat SMART_ENTITY_FORMAT = new VertexFormat(
            ImmutableMap.<String, VertexFormatElement>builder()
                    .put("Position", VertexFormats.POSITION_ELEMENT)
                    .put("Color", VertexFormats.COLOR_ELEMENT)
                    .put("UV0", VertexFormats.TEXTURE_0_ELEMENT)
                    .put("Normal", VertexFormats.NORMAL_ELEMENT)
                    .put("Padding", VertexFormats.PADDING_ELEMENT)
                    .put("Id", new VertexFormatElement(0, VertexFormatElement.DataType.UINT, VertexFormatElement.Type.UV, 1))
                    .build());

    public static RenderLayer turnIntoSmartRenderLayer(@Nullable RenderLayer dumbRenderLayer) {
        if (dumbRenderLayer == null) {
            return null;
        }

        RenderLayer.MultiPhase dumbMultiPhaseRenderPass = ((RenderLayer.MultiPhase) dumbRenderLayer);
        MultiPhaseParametersAccessor dumbMultiPhaseParameters = ((MultiPhaseParametersAccessor) (Object) ((MultiPhaseRenderPassAccessor) (Object) dumbMultiPhaseRenderPass).getPhases());

        RenderLayer.MultiPhaseParameters phaseParameters = RenderLayer.MultiPhaseParameters.builder()
                .cull(dumbMultiPhaseParameters.getCull())
                .depthTest(dumbMultiPhaseParameters.getDepthTest())
                .layering(dumbMultiPhaseParameters.getLayering())
                .lightmap(dumbMultiPhaseParameters.getLightmap())
                .lineWidth(dumbMultiPhaseParameters.getLineWidth())
                .overlay(dumbMultiPhaseParameters.getOverlay())
                .shader(new RenderPhase.Shader(RenderSystem::getShader))
                .target(dumbMultiPhaseParameters.getTarget())
                .texture(dumbMultiPhaseParameters.getTexture())
                .texturing(dumbMultiPhaseParameters.getTexturing())
                .transparency(dumbMultiPhaseParameters.getTransparency())
                .writeMaskState(dumbMultiPhaseParameters.getWriteMaskState())
                .build(dumbMultiPhaseParameters.getOutlineMode());

        return new RenderLayer.MultiPhase(
                ((RenderPhaseAccessor) (Object) dumbMultiPhaseRenderPass).getName(),
                SMART_ENTITY_FORMAT,
                VertexFormat.DrawMode.QUADS,
                ((RenderLayerAccessor) (Object) dumbMultiPhaseRenderPass).getExpectedBufferSize(),
                ((RenderLayerAccessor) (Object) dumbMultiPhaseRenderPass).getHasCrumbling(),
                ((RenderLayerAccessor) (Object) dumbMultiPhaseRenderPass).getTranslucent(),
                phaseParameters
        );
    }

    @Override
    public void onInitializeClient() {
        try {
            System.loadLibrary("renderdoc");
        } catch (Exception e) {
            System.err.println("Unable to load renderdoc");
        }

        if (EXPORT_MODELS_TO_OBJ) {
            try {
                if (!Files.exists(FabricLoader.getInstance().getGameDir().resolve("models"))) {
                    Files.createDirectory(FabricLoader.getInstance().getGameDir().resolve("models"));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            Map<EntityModelLayer, TexturedModelData> models = EntityModels.getModels();
            models.forEach((entityModelLayer, modelData) -> {
                ModelPart model = modelData.createModel();
                MatrixStack stack = new MatrixStack();
                BufferBuilder consumer = new BufferBuilder(2097152);
                consumer.begin(VertexFormat.DrawMode.QUADS, SMART_ENTITY_FORMAT);
                model.render(stack, consumer, 0, 0);
                consumer.end();
                Pair<BufferBuilder.DrawArrayParameters, ByteBuffer> data = consumer.popData();
                List<String> vertices = new ArrayList<>(), normals = new ArrayList<>(), uvs = new ArrayList<>();
                ByteBuffer vertexData = data.getSecond();
                vertexData.order(ByteOrder.LITTLE_ENDIAN);
                for (int i = 0; i < vertexData.limit(); i += SMART_ENTITY_FORMAT.getVertexSize()) {
                    vertices.add(String.format("v %f %f %f",
                            vertexData.getFloat(i),
                            vertexData.getFloat(i + 4),
                            vertexData.getFloat(i + 8)));

                    uvs.add(String.format("vt %f %f",
                            vertexData.getFloat(i + 16),
                            vertexData.getFloat(i + 20)));

                    normals.add(String.format("vn %f %f %f",
                            vertexData.get(i + 24) / 127.0f,
                            vertexData.get(i + 25) / 127.0f,
                            vertexData.get(i + 26) / 127.0f));
                }

                List<String> faces = new ArrayList<>();
                for (int i = 1; i < vertices.size() + 1; i += 4) {
                    faces.add(String.format("f %d/%d/%d %d/%d/%d %d/%d/%d", i, i, i, i + 1, i + 1, i + 1, i + 2, i + 2, i + 2));
                    faces.add(String.format("f %d/%d/%d %d/%d/%d %d/%d/%d", i + 2, i + 2, i + 2, i + 3, i + 3, i + 3, i, i, i));
                }
                String output = String.join("\n", vertices) + "\n" + String.join("\n", uvs) + "\n" + String.join("\n", normals) + "\n" + String.join("\n", faces);
                try {
                    Files.writeString(FabricLoader.getInstance().getGameDir().resolve("models").resolve("output_" + entityModelLayer.getId().getPath().replace("/", "_") + "_" + entityModelLayer.getName() + ".obj"), output);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }


    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }
}
