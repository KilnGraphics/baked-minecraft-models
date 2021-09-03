package com.oroarmor.bakedminecraftmodels.vertex;

import com.oroarmor.bakedminecraftmodels.mixin.buffer.BufferBuilderAccessor;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

public class SmartBufferBuilderWrapper implements VertexConsumer {
    private final BufferBuilder internalBufferBuilder;

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

    public void begin(VertexFormat.DrawMode drawMode, VertexFormat vertexFormat) {
        internalBufferBuilder.begin(drawMode, vertexFormat);
    }

    public void end() {
        internalBufferBuilder.end();
    }

    public void clear() {
        internalBufferBuilder.clear();
    }

    public BufferBuilder getInternalBufferBuilder() {
        return internalBufferBuilder;
    }

}
