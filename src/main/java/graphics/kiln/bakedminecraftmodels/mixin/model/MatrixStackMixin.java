package graphics.kiln.bakedminecraftmodels.mixin.model;

import graphics.kiln.bakedminecraftmodels.access.ModelContainer;
import graphics.kiln.bakedminecraftmodels.model.VboBackedModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MatrixStack.class)
public class MatrixStackMixin implements ModelContainer {

    private VboBackedModel model;

    @Override
    public VboBackedModel getModel() {
        return model;
    }

    @Override
    public void setModel(VboBackedModel model) {
        this.model = model;
    }
}
