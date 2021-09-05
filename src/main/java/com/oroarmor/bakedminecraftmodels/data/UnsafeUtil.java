package com.oroarmor.bakedminecraftmodels.data;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Matrix4f;
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class UnsafeUtil {

    private static final Unsafe UNSAFE = findUnsafe();
    private static final Object INTERNAL_UNSAFE;
    private static final long MATRIX4F_A00_FIELD_OFFSET;
    private static final MethodHandle COPY_MEMORY_HANDLE;

    static {
        long offset1 = -1;
        MethodHandle tempHandle = null;
        Object tempInternalUnsafe = null;

        try {
            Field firstField = Matrix4f.class.getDeclaredField(FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_1159", "field_21652", "F"));
            offset1 = UNSAFE.objectFieldOffset(firstField);
            Class<?> internalUnsafeClass = Class.forName("jdk.internal.misc.Unsafe");
            Method method = internalUnsafeClass.getDeclaredMethod("copyMemory0", Object.class, long.class, Object.class, long.class, long.class);
            method.setAccessible(true);
            tempHandle = MethodHandles.lookup().unreflect(method);
            tempHandle = tempHandle.asType(MethodType.methodType(void.class, Object.class, Matrix4f.class, long.class, Void.class, long.class, long.class));
            tempInternalUnsafe = internalUnsafeClass.getMethod("getUnsafe").invoke(null);
        } catch (Throwable ignored) {
            BakedMinecraftModels.LOGGER.info("Unsafe matrix copies unavailable, falling back to default copies");
        }

        COPY_MEMORY_HANDLE = tempHandle;
        INTERNAL_UNSAFE = tempInternalUnsafe;
        MATRIX4F_A00_FIELD_OFFSET = offset1;
    }

    private static Unsafe findUnsafe() {
        for (Field field : Unsafe.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();

            if (field.getType() == Unsafe.class && Modifier.isStatic(modifiers)) {
                try {
                    field.setAccessible(true);
                    return (Unsafe) field.get(null);
                } catch (Exception ignored) {
                }
            }
        }

        throw new Error("Unsafe exists but an unsafe instance doesn't exist?");
    }

    // returns true if successful. if false, use a fallback function
    public static boolean writeMatrix4fUnsafe(long pointer, Matrix4f matrix) {
        if (MATRIX4F_A00_FIELD_OFFSET != -1) {
            try {
                COPY_MEMORY_HANDLE.invokeExact(INTERNAL_UNSAFE, matrix, MATRIX4F_A00_FIELD_OFFSET, null, pointer, 16L * Float.BYTES);
            } catch(Throwable t) {
                BakedMinecraftModels.LOGGER.error("bad", t);
            }
            return true;
        }

        return false;
    }
}
