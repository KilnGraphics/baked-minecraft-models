package com.oroarmor.bakedminecraftmodels.mixin;

import net.minecraft.client.render.Shader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Shader.class)
public interface ShaderAccessor {
    @Accessor
    List<Integer> getLoadedSamplerIds();

    @Accessor
    List<String> getSamplerNames();
}
