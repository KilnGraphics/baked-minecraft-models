package com.oroarmor.bakedminecraftmodels.mixin;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexFormat;

@Mixin(RenderLayer.class)
public class RenderLayerMixin {
    @ModifyArg(method = {"method_34827", "method_34826", "method_34831", "method_34832", "method_34825", }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/RenderLayer;of(Ljava/lang/String;Lnet/minecraft/client/render/VertexFormat;Lnet/minecraft/client/render/VertexFormat$DrawMode;IZZLnet/minecraft/client/render/RenderLayer$MultiPhaseParameters;)Lnet/minecraft/client/render/RenderLayer$MultiPhase;")) // TODO: finish all the render layers that need this format
    private static VertexFormat replaceWithSmarterFormat(VertexFormat original) {
        return BakedMinecraftModels.SMART_ENTITY_FORMAT;
    }
}
