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
    private static final long MATRIX4F_A00_FIELD_OFFSET;

    static {
        long offset1 = -1;

        try {
            Field firstField = Matrix4f.class.getDeclaredField(FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_1159", "field_21652", "F"));
            offset1 = UNSAFE.objectFieldOffset(firstField);
        } catch (Throwable ignored) {
            BakedMinecraftModels.LOGGER.info("Unsafe matrix copies unavailable, falling back to default copies");
        }

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

    public static boolean writeMatrix4fUnsafe(long pointer, Matrix4f matrix) {
        if (MATRIX4F_A00_FIELD_OFFSET != -1) {
            UNSAFE.putLong(pointer, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET));
            UNSAFE.putLong(pointer + 8, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET + 8));
            UNSAFE.putLong(pointer + 16, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET + 16));
            UNSAFE.putLong(pointer + 24, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET + 24));
            UNSAFE.putLong(pointer + 32, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET + 32));
            UNSAFE.putLong(pointer + 40, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET + 40));
            UNSAFE.putLong(pointer + 48, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET + 48));
            UNSAFE.putLong(pointer + 56, UNSAFE.getLong(matrix, MATRIX4F_A00_FIELD_OFFSET + 56));
            return true;
        }
        return false;
    }
}
