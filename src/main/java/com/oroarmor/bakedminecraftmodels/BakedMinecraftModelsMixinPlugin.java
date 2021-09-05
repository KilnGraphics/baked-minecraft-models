/*
 * MIT License
 *
 * Copyright (c) 2021 OroArmor (Eli Orona), Blaze4D
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.oroarmor.bakedminecraftmodels;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.*;

public class BakedMinecraftModelsMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        // noop
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // noop
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // noop
    }

    private static final Object2IntMap<String> MATRIX4F_FIELD_INDEX_MAP;

    static {
        Object2IntMap<String> tempMap = new Object2IntOpenHashMap<>();
        tempMap.put(mapFieldName("field_21652", "net.minecraft.class_1159", "F"), 0); // a00
        tempMap.put(mapFieldName("field_21656", "net.minecraft.class_1159", "F"), 1); // a10
        tempMap.put(mapFieldName("field_21660", "net.minecraft.class_1159", "F"), 2); // a20
        tempMap.put(mapFieldName("field_21664", "net.minecraft.class_1159", "F"), 3); // a30
        tempMap.put(mapFieldName("field_21653", "net.minecraft.class_1159", "F"), 4); // a01
        tempMap.put(mapFieldName("field_21657", "net.minecraft.class_1159", "F"), 5); // a11
        tempMap.put(mapFieldName("field_21661", "net.minecraft.class_1159", "F"), 6); // a21
        tempMap.put(mapFieldName("field_21665", "net.minecraft.class_1159", "F"), 7); // a31
        tempMap.put(mapFieldName("field_21654", "net.minecraft.class_1159", "F"), 8); // a02
        tempMap.put(mapFieldName("field_21658", "net.minecraft.class_1159", "F"), 9); // a12
        tempMap.put(mapFieldName("field_21662", "net.minecraft.class_1159", "F"), 10); // a22
        tempMap.put(mapFieldName("field_21666", "net.minecraft.class_1159", "F"), 11); // a32
        tempMap.put(mapFieldName("field_21655", "net.minecraft.class_1159", "F"), 12); // a03
        tempMap.put(mapFieldName("field_21659", "net.minecraft.class_1159", "F"), 13); // a13
        tempMap.put(mapFieldName("field_21663", "net.minecraft.class_1159", "F"), 14); // a23
        tempMap.put(mapFieldName("field_21667", "net.minecraft.class_1159", "F"), 15); // a33
        tempMap.defaultReturnValue(-1);
        MATRIX4F_FIELD_INDEX_MAP = Object2IntMaps.unmodifiable(tempMap);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (classNameEqualsMapped("net.minecraft.class_1159", targetClassName)) {
            FieldNode[] resortedFields = new FieldNode[16];
            ListIterator<FieldNode> fieldIterator = targetClass.fields.listIterator();
            while (fieldIterator.hasNext()) {
                FieldNode field = fieldIterator.next();

                int newFieldIndex = MATRIX4F_FIELD_INDEX_MAP.getInt(field.name);
                if (newFieldIndex != -1) {
                    resortedFields[newFieldIndex] = field;
                    fieldIterator.remove();
                }
            }

            targetClass.fields.addAll(Arrays.asList(resortedFields));
        }
    }

    private static boolean classNameEqualsMapped(String className, String runtimeClassName) {
        return runtimeClassName.equals(FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", className));
    }

    private static String mapFieldName(String fieldName, String className, String descriptor) {
        return FabricLoader.getInstance().getMappingResolver().mapFieldName("intermediary", className, fieldName, descriptor);
    }
}
