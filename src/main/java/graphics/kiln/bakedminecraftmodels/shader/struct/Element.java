package graphics.kiln.bakedminecraftmodels.shader.struct;

public interface Element {
    int getCount();
    ElementType getType();
    String toShaderString();
}
