/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.vertex;

import graphics.kiln.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

import java.nio.FloatBuffer;

public class SmartBufferBuilderWrapper implements VertexConsumer {
    private final BufferBuilder internalBufferBuilder;

    // Keep an on-CPU copy of the vertex data - this is used to prepare transparency sorting data
    private FloatBuffer vertexPositions = FloatBuffer.allocate(64 * 3);

    public SmartBufferBuilderWrapper(BufferBuilder internalBufferBuilder) {
        this.internalBufferBuilder = internalBufferBuilder;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        // Keep track of the vertex positions with an on-CPU buffer
        // Only store them as single-precision floats, that's plenty for transparency sorting
        if (!vertexPositions.hasRemaining()) {
            FloatBuffer newFB = FloatBuffer.allocate(vertexPositions.capacity() + 128 * 3);
            vertexPositions.flip();
            newFB.put(vertexPositions);
            vertexPositions = newFB;
        }
        vertexPositions.put((float) x).put((float) y).put((float) z);

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

        vertex(x, y, z); // Make sure we call this, to record the verts for the transparency stuff
        internalBufferBuilder.texture(u, v).normal(normalX, normalY, normalZ);
        originalAccessor.getBuffer().putInt(originalAccessor.getElementOffset(), partId);
        internalBufferBuilder.nextElement();
        internalBufferBuilder.next();
    }

    private int partId;

    public void setId(int partId) {
        this.partId = partId;
    }

    public void begin(VertexFormat.DrawMode drawMode, VertexFormat vertexFormat) {
        internalBufferBuilder.begin(drawMode, vertexFormat);
    }

    public void end() {
        internalBufferBuilder.end();
        vertexPositions.flip();
    }

    public void clear() {
        internalBufferBuilder.clear();
        vertexPositions.clear();
    }

    public BufferBuilder getInternalBufferBuilder() {
        return internalBufferBuilder;
    }

    public FloatBuffer getVertexPositions() {
        return vertexPositions;
    }
}
