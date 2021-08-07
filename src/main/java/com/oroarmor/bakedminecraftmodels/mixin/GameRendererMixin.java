package com.oroarmor.bakedminecraftmodels.mixin;

import java.io.IOException;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Shader;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.resource.ResourceFactory;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Redirect(method = "loadShaders", at = @At(value = "NEW", target = "net/minecraft/client/render/Shader")) // TODO: finish all the shaders that need this format
    public Shader redirectShaderCreation(ResourceFactory factory, String name, VertexFormat format) throws IOException {
        if (name.equals("rendertype_entity_cutout_no_cull")) {
            return new Shader(factory, name, BakedMinecraftModels.SMART_ENTITY_FORMAT);
        }
        return new Shader(factory, name, format);
    }
}
