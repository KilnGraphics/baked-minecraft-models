package com.oroarmor.bakedminecraftmodels.mixin.renderlayer;

import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.Shader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;
import java.util.function.Supplier;

@Mixin(RenderPhase.Shader.class)
public interface RenderPhaseShaderAccessor {
    @Accessor
    Optional<Supplier<Shader>> getSupplier();
}
