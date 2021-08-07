package com.oroarmor.bakedminecraftmodels.mixin;

import java.util.List;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import com.oroarmor.bakedminecraftmodels.access.ModelID;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.SpriteTexturedVertexConsumer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

@Mixin(ModelPart.class)
public class ModelPartMixin implements ModelID {
    @Unique
    private int bmm$id;

    @Shadow
    private List<ModelPart.Cuboid> cuboids;

    @Override
    public void setId(int id) {
        bmm$id = id;
        cuboids.forEach(cuboid -> ((ModelID) cuboid).setId(bmm$id));
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;II)V",
            at = @At("HEAD"), argsOnly = true)
    public VertexConsumer setNewConsumer(VertexConsumer vertices) {
        return getVertexConsumer(vertices);
    }

    @ModifyVariable(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFF)V",
            at = @At("HEAD"), argsOnly = true)
    public VertexConsumer setNewConsumerComplex(VertexConsumer vertices) {
        return getVertexConsumer(vertices);
    }

    @NotNull
    @Unique
    private VertexConsumer getVertexConsumer(VertexConsumer vertices) {
        if (vertices instanceof SpriteTexturedVertexConsumer) {
            vertices = (((SpriteTexturedVertexConsumerAccessor) vertices).getParent());
        }

        if (((BufferBuilderAccessor) vertices).getFormat() == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) {
            BufferBuilder bufferBuilder = new BufferBuilder(1000);
            MemoryUtil.memFree(((BufferBuilderAccessor) bufferBuilder).getBuffer());
            ((BufferBuilderAccessor) bufferBuilder).setBuffer(((BufferBuilderAccessor) vertices).getBuffer());
            bufferBuilder.begin(VertexFormat.DrawMode.QUADS, BakedMinecraftModels.SMART_ENTITY_FORMAT);
            return bufferBuilder;
        }
        return vertices;
    }
}