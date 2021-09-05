package com.oroarmor.bakedminecraftmodels.data;

import com.oroarmor.bakedminecraftmodels.BakedMinecraftModels;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeDataSupport;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

public class UnsafeUtil {

    private static final Unsafe UNSAFE = findUnsafe();
    private static final long MATRIX4F_A00_FIELD_OFFSET;

    static {
        long offset = -1;

        try {
            Field firstField = Matrix4f.class.getDeclaredField(FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", "net.minecraft.class_1159", "field_21652", "F"));
            offset = UNSAFE.objectFieldOffset(firstField);
        } catch (Throwable ignored) {
            BakedMinecraftModels.LOGGER.info("Unsafe matrix copies unavailable, falling back to default copies");
        }

        MATRIX4F_A00_FIELD_OFFSET = offset;
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
            MemoryUtil.memCopy(Pain.addressOf(matrix) + MATRIX4F_A00_FIELD_OFFSET, pointer, 16 * Float.BYTES);
            return true;
        }

        return false;
    }

    // inspired by https://github.com/openjdk/jol/blob/master/jol-core/src/main/java/org/openjdk/jol/vm/HotspotUnsafe.java
    private static class Pain {

        private static final Unsafe unsafe = UNSAFE;
        private static final int addressSize;
        private static final int objectAlignment;
        private static final int oopSize;
        private static final boolean compressedOopsEnabled;
        private static final long narrowOopBase;
        private static final int narrowOopShift;
        private static final long arrayObjectBase;

        static {
            arrayObjectBase = unsafe.arrayBaseOffset(Object[].class);
            addressSize = unsafe.addressSize();

            oopSize = guessOopSize();
            compressedOopsEnabled = Objects.requireNonNullElseGet(pollCompressedOops(), () -> (addressSize != oopSize));
            objectAlignment = Objects.requireNonNullElseGet(pollObjectAlignment(), Pain::guessAlignment);
            narrowOopShift = compressedOopsEnabled ? log2p(objectAlignment) : 0;
            narrowOopBase = guessNarrowOopBase();
        }

        private static <T> T get(String key, Function<String, T> function) {
            try {
                MBeanServer server = ManagementFactory.getPlatformMBeanServer();
                ObjectName mbean = new ObjectName("com.sun.management:type=HotSpotDiagnostic");
                CompositeDataSupport val = (CompositeDataSupport) server.invoke(mbean, "getVMOption", new Object[]{key}, new String[]{"java.lang.String"});
                return function.apply(val.get("value").toString());
            } catch (Throwable ignored) {
            }

            return null;
        }

        private static Boolean pollCompressedOops() {
            return get("UseCompressedOops", Boolean::valueOf);
        }

        private static Integer pollObjectAlignment() {
            return get("ObjectAlignmentInBytes", Integer::valueOf);
        }

        private static long guessNarrowOopBase() {
            return addressOf(null);
        }

        private static int guessOopSize() {
            // When running with CompressedOops on 64-bit platform, the address size
            // reported by Unsafe is still 8, while the real reference fields are 4 bytes long.
            // Try to guess the reference field size with this naive trick.
            int oopSize;

            try {
                class CompressedOopsClass {
                    public Object obj1;
                    public Object obj2;
                }

                long off1 = unsafe.objectFieldOffset(CompressedOopsClass.class.getField("obj1"));
                long off2 = unsafe.objectFieldOffset(CompressedOopsClass.class.getField("obj2"));
                oopSize = (int) Math.abs(off2 - off1);
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("Infrastructure failure", e);
            }

            return oopSize;
        }

        private static int guessAlignment() {
            Random r = new Random();
            long min = -1;

            for (int c = 0; c < 100000; c++) {
                Object o1 = instantiateType(r.nextInt(5));
                Object o2 = instantiateType(r.nextInt(5));
                long diff = Math.abs(addressOf(o1) - addressOf(o2));

                if (min == -1) {
                    min = diff;
                } else {
                    min = gcd(min, diff);
                }
            }

            return (int) min;
        }

        private static Object instantiateType(int type) {
            class MyObject1 {
            }

            class MyObject2 {
                private boolean b;
            }

            class MyObject3 {
                private int i;
            }

            class MyObject4 {
                private long l;
            }

            class MyObject5 {
                private Object o;
            }

            return switch (type) {
                case 0 -> new MyObject1();
                case 1 -> new MyObject2();
                case 2 -> new MyObject3();
                case 3 -> new MyObject4();
                case 4 -> new MyObject5();
                default -> throw new IllegalStateException();
            };
        }

        public static long addressOf(Object o) {
            Object[] array = new Object[]{o};

            long objectAddress = switch (oopSize) {
                case 4 -> unsafe.getInt(array, arrayObjectBase) & 0xFFFFFFFFL;
                case 8 -> unsafe.getLong(array, arrayObjectBase);
                default -> throw new Error("unsupported address size: " + oopSize);
            };

            array[0] = null;

            return toNativeAddress(objectAddress);
        }

        private static long toNativeAddress(long address) {
            if (compressedOopsEnabled) {
                return narrowOopBase + (address << narrowOopShift);
            } else {
                return address;
            }
        }

        // floor(log2(x))
        private static int log2p(int x) {
            int r = 0;
            while ((x >>= 1) != 0)
                r++;
            return r;
        }

        private static long gcd(long a, long b) {
            while (b > 0) {
                long temp = b;
                b = a % b;
                a = temp;
            }

            return a;
        }
    }
}
