package graphics.kiln.bakedminecraftmodels.mixin;

import graphics.kiln.bakedminecraftmodels.debug.DebugInfo;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(DebugHud.class)
public class DebugHudMixin {

    @Inject(method = "getLeftText", at = @At("RETURN"))
    private void addInstancingText(CallbackInfoReturnable<List<String>> cir) {
        List<String> strings = cir.getReturnValue();
        strings.add("[Baked Models] Model Buffer: " + DebugInfo.getSizeReadable(DebugInfo.currentModelBufferSize) + DebugInfo.MODEL_BUFFER_SUFFIX);
        strings.add("[Baked Models] Part Buffer: " + DebugInfo.getSizeReadable(DebugInfo.currentPartBufferSize) + DebugInfo.PART_BUFFER_SUFFIX);

        int totalInstances = 0;
        int totalSets = 0;
        List<String> tempStrings = new ArrayList<>();
        for (Map.Entry<String, DebugInfo.ModelDebugInfo> entry : DebugInfo.modelToDebugInfoMap.entrySet()) {
            DebugInfo.ModelDebugInfo modelDebugInfo = entry.getValue();
            tempStrings.add("[Baked Models] " + entry.getKey() + ": " + modelDebugInfo.instances + " Instances / " + modelDebugInfo.sets + " Sets");
            totalInstances += modelDebugInfo.instances;
            totalSets += modelDebugInfo.sets;
        }
        strings.add("[Baked Models] Total: " + totalInstances + " Instances / " + totalSets + " Sets");
        strings.addAll(tempStrings);

        DebugInfo.currentModelBufferSize = 0;
        DebugInfo.currentPartBufferSize = 0;
        DebugInfo.modelToDebugInfoMap.clear();
    }
}
