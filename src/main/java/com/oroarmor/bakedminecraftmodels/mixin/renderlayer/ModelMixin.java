package com.oroarmor.bakedminecraftmodels.mixin.renderlayer;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModelsRenderLayerManager;
import com.oroarmor.bakedminecraftmodels.model.VboBackedModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Model.class)
public abstract class ModelMixin {
    @Inject(method = "getLayer", at = @At("RETURN"), cancellable = true)
    private void useSmarterRenderLayer(Identifier texture, CallbackInfoReturnable<RenderLayer> cir) {
        if (this instanceof VboBackedModel) {
            cir.setReturnValue(BakedMinecraftModelsRenderLayerManager.tryDeriveSmartRenderLayer(cir.getReturnValue()));
        }
    }
}
