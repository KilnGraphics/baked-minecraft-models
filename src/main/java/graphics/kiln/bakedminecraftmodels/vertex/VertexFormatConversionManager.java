package graphics.kiln.bakedminecraftmodels.vertex;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import graphics.kiln.bakedminecraftmodels.mixin.vertex.VertexFormatAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VertexFormatConversionManager {

    private final Map<VertexFormat, VertexFormat> convertedFormatsMap;

    public VertexFormatConversionManager() {
        convertedFormatsMap = new Object2ObjectOpenHashMap<>();
    }

    public VertexFormat convert(VertexFormat format) {
        return convertedFormatsMap.computeIfAbsent(format, source -> {
            ImmutableMap<String, VertexFormatElement> oldElements = ((VertexFormatAccessor) source).getElementMap();
            Map<String, VertexFormatElement> newElements = new Object2ObjectOpenHashMap<>(oldElements.size());

            return null;
        });
    }
}
