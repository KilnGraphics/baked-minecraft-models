/*
 * MIT License
 *
 * Copyright (c) 2021 OroArmor (Eli Orona), Blaze4D
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oroarmor.bakedminecraftmodels.mixin.model;

import java.util.HashSet;
import java.util.Set;

import com.oroarmor.bakedminecraftmodels.access.ModelID;
import com.oroarmor.bakedminecraftmodels.access.ModelIDBuilder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

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
    private final Set<Integer> bmm$reusableIds = new HashSet<>();

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
        if (getParent() == null) {
            if (bmm$reusableIds.isEmpty()) {
                return bmm$nextId++;
            }
            int id = bmm$reusableIds.stream().sorted().findFirst().orElse(-1);
            if (!bmm$reusableIds.remove(id)){
                throw new RuntimeException("That's a real issue");
            }
            return id;
        }
        return getParent().getNextAvailableModelId();
    }

    @Inject(method = "addChild", at = @At("RETURN"))
    public void addChildID(String name, ModelPartBuilder builder, ModelTransform rotationData, CallbackInfoReturnable<ModelPartData> cir) {
        ModelIDBuilder child = (ModelIDBuilder) cir.getReturnValue();
        child.setParent(this);
        child.setId(this.getNextAvailableModelId());
    }

    @Inject(method = "addChild", at = @At(value = "INVOKE", target = "Ljava/util/Map;putAll(Ljava/util/Map;)V"), locals = LocalCapture.CAPTURE_FAILSOFT)
    public void removeOldId(String name, ModelPartBuilder builder, ModelTransform rotationData, CallbackInfoReturnable<ModelPartData> cir, ModelPartData modelPartData, ModelPartData modelPartData2){
        if (modelPartData2 != null) {
            bmm$makeIdReusable(((ModelID) modelPartData2).getId());
        }
    }

    @Unique
    private void bmm$makeIdReusable(int id) {
        ModelIDBuilder parent = this;
        while (parent != null && parent.getParent() != null) {
            parent = parent.getParent();
        }

        ((ModelPartDataMixin) parent).bmm$reusableIds.add(id);
    }

    @Inject(method = "createPart", at = @At("RETURN"))
    public void setModelPartID(int textureWidth, int textureHeight, CallbackInfoReturnable<ModelPart> cir) {
        ((ModelID) (Object) cir.getReturnValue()).setId(this.bmm$id);
    }
}
