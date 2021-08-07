package com.oroarmor.bakedminecraftmodels.mixin;

import com.oroarmor.bakedminecraftmodels.access.ModelID;
import com.oroarmor.bakedminecraftmodels.access.ModelIDBuilder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;

@Mixin(ModelPartData.class)
public class ModelPartDataMixin implements ModelIDBuilder {
    @Unique
    private int bmm$id = 0;

    @Unique
    private int bmm$nextId = 1;

    @Unique
    @Nullable
    private ModelIDBuilder bmm$parent;

    @Override
    public void setId(int id) {
        bmm$id = id;
        bmm$nextId = id + 1;
    }

    @Override
    public int getId() {
        return bmm$id;
    }

    @Override
    public void setParent(ModelIDBuilder parent) {
        bmm$parent = parent;
    }

    @Override
    public @Nullable ModelIDBuilder getParent() {
        return bmm$parent;
    }

    @Override
    public int getNextAvailableModelId() {
        return getParent() == null ? bmm$nextId++ : getParent().getNextAvailableModelId();
    }

    @Inject(method = "addChild", at = @At("RETURN"))
    public void addChildID(String name, ModelPartBuilder builder, ModelTransform rotationData, CallbackInfoReturnable<ModelPartData> cir) {
        ModelIDBuilder child = (ModelIDBuilder) cir.getReturnValue();
        child.setParent(this);
        child.setId(this.getNextAvailableModelId());
    }

    @Inject(method = "createPart", at = @At("RETURN"))
    public void setModelPartID(int textureWidth, int textureHeight, CallbackInfoReturnable<ModelPart> cir) {
        ((ModelID) (Object) cir.getReturnValue()).setId(this.bmm$id);
    }
}
