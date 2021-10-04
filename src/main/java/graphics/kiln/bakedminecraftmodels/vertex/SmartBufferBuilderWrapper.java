/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.vertex;

import graphics.kiln.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

public class SmartBufferBuilderWrapper implements VertexConsumer {
    private final BufferBuilder internalBufferBuilder;
    private VboBackedModel currentModel;

    public SmartBufferBuilderWrapper(BufferBuilder internalBufferBuilder) {
        this.internalBufferBuilder = internalBufferBuilder;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        return internalBufferBuilder.vertex(x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        return internalBufferBuilder.color(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        return internalBufferBuilder.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        return internalBufferBuilder.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v) {
        return internalBufferBuilder.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        return internalBufferBuilder.normal(x, y, z);
    }

    @Override
    public void next() {
        internalBufferBuilder.next();
    }

    @Override
    public void fixedColor(int red, int green, int blue, int alpha) {
        internalBufferBuilder.fixedColor(red, green, blue, alpha);
    }

    @Override
    public void unfixColor() {
        internalBufferBuilder.unfixColor();
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        BufferBuilderAccessor originalAccessor = (BufferBuilderAccessor) internalBufferBuilder;

        internalBufferBuilder.vertex(x, y, z).texture(u, v).normal(normalX, normalY, normalZ);
        originalAccessor.getBuffer().putInt(originalAccessor.getElementOffset(), partId);
        internalBufferBuilder.nextElement();
        internalBufferBuilder.next();
    }

    private int partId;

    public void setId(int partId) {
        this.partId = partId;
    }

    public void begin(VertexFormat.DrawMode drawMode, VertexFormat vertexFormat, VboBackedModel model) {
        currentModel = model;
        internalBufferBuilder.begin(drawMode, vertexFormat);
    }

    public void end() {
        internalBufferBuilder.end();
        currentModel = null;
    }

    public void clear() {
        internalBufferBuilder.clear();
    }

    public BufferBuilder getInternalBufferBuilder() {
        return internalBufferBuilder;
    }

    public VboBackedModel getCurrentModel() {
        return currentModel;
    }

}
