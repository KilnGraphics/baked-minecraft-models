package com.oroarmor.bakedminecraftmodels.mixin;

import com.oroarmor.bakedminecraftmodels.data.ModelInstanceData;
import com.oroarmor.bakedminecraftmodels.data.UnsafeUtil;
import net.minecraft.client.main.Main;
import net.minecraft.obfuscate.DontObfuscate;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Main.class)
public class MainMixin {
    /**
     * @author The Maldster
     */
    @DontObfuscate
    @Overwrite
    public static void main(String[] args) {
        long pointer = MemoryUtil.nmemAlloc(64 * 10000000);
        Matrix4f swagMatrix = Matrix4f.projectionMatrix(342,234,234,34,34,234);
        long offset, t1, t2;

        offset = 0;
        for (; offset < 64 * 10000000; offset += 64) {
            ModelInstanceData.writeMatrix4f(pointer + offset, swagMatrix);
        }
        offset = 0;
        t1 = System.nanoTime();
        for (; offset < 64 * 10000000; offset += 64) {
            ModelInstanceData.writeMatrix4f(pointer + offset, swagMatrix);
        }
        t2 = System.nanoTime();
        System.out.println(t2 - t1 + " nanos normal");

        offset = 0;
        for (; offset < 64 * 10000000; offset += 64) {
            UnsafeUtil.writeMatrix4fUnsafe(pointer + offset, swagMatrix);
        }
        offset = 0;
        t1 = System.nanoTime();
        for (; offset < 64 * 10000000; offset += 64) {
            UnsafeUtil.writeMatrix4fUnsafe(pointer + offset, swagMatrix);
        }
        t2 = System.nanoTime();
        System.out.println(t2 - t1 + " nanos normalLongs");

        MemoryUtil.nmemFree(pointer);
    }
}
