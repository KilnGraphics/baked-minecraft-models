package com.oroarmor.bakedminecraftmodels.access;

import org.jetbrains.annotations.Nullable;

public interface ModelIDBuilder extends ModelID {
    void setParent(ModelIDBuilder parent);
    @Nullable ModelIDBuilder getParent();

    int getNextAvailableModelId();
}
