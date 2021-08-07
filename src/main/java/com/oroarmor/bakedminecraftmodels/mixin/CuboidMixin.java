package com.oroarmor.bakedminecraftmodels.mixin;

import com.oroarmor.bakedminecraftmodels.access.ModelID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferVertexConsumer;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;

@Mixin(ModelPart.Cuboid.class)
public class CuboidMixin implements ModelID {
    @Unique
    private int bmm$id;

    @Override
    public void setId(int id) {
        bmm$id = id;
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @Redirect(method = "renderCuboid", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumer;vertex(FFFFFFFFFIIFFF)V"))
    public void setVertexID(VertexConsumer vertexConsumer, float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ) {
        vertexConsumer.vertex(x, y, z);
        vertexConsumer.color(red, green, blue, alpha);
        vertexConsumer.texture(u, v);
//        vertexConsumer.overlay(overlay);
//        vertexConsumer.light(light);
        vertexConsumer.normal(normalX, normalY, normalZ);
        if (vertexConsumer instanceof SpriteTexturedVertexConsumer) {
            ((BufferVertexConsumer) ((SpriteTexturedVertexConsumerAccessor) vertexConsumer).getParent()).putShort(0, (short) bmm$id);
        } else {
            ((BufferVertexConsumer) vertexConsumer).putShort(0, (short) bmm$id);
        }
        ((BufferVertexConsumer) vertexConsumer).nextElement();

        vertexConsumer.next();
    }

}
