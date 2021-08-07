package com.oroarmor.bakedminecraftmodels.mixin;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
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
        BufferVertexConsumer parent = vertexConsumer instanceof SpriteTexturedVertexConsumer ?
                (BufferVertexConsumer) ((SpriteTexturedVertexConsumerAccessor) vertexConsumer).getParent() :
                (BufferVertexConsumer) vertexConsumer;

        if (((BufferBuilderAccessor) parent).getFormat() != BakedMinecraftModels.SMART_ENTITY_FORMAT) {
            parent.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
        } else {
            parent.vertex(x, y, z);
            parent.color(red, green, blue, alpha);
            parent.texture(u, v);
//        vertexConsumer.overlay(overlay);
//        vertexConsumer.light(light);
            parent.normal(normalX, normalY, normalZ);
            parent.putShort(0, (short) bmm$id);
            parent.putShort(2, (short) (bmm$id >> 8));
            ((BufferVertexConsumer) vertexConsumer).nextElement();

            vertexConsumer.next();
        }
    }
}
