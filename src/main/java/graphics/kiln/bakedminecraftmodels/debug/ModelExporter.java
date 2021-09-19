/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.debug;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.datafixers.util.Pair;
import graphics.kiln.bakedminecraftmodels.BakedMinecraftModelsVertexFormats;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.entity.model.EntityModelLayer;
import net.minecraft.client.render.entity.model.EntityModels;
import net.minecraft.client.util.math.MatrixStack;

import net.fabricmc.loader.api.FabricLoader;

public class ModelExporter {
    public static void exportDefaultModelsToOBJ() {
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
            consumer.begin(VertexFormat.DrawMode.QUADS, BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT);
            model.render(stack, consumer, 0, 0);
            consumer.end();
            Pair<BufferBuilder.DrawArrayParameters, ByteBuffer> data = consumer.popData();
            List<String> vertices = new ArrayList<>(), normals = new ArrayList<>(), uvs = new ArrayList<>();
            ByteBuffer vertexData = data.getSecond();
            vertexData.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < vertexData.limit(); i += BakedMinecraftModelsVertexFormats.SMART_ENTITY_FORMAT.getVertexSize()) {
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
