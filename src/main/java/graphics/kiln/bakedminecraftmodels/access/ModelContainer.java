package graphics.kiln.bakedminecraftmodels.access;

import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;

public interface ModelContainer {
    VboBackedModel getModel();
    void setModel(VboBackedModel model);
}
