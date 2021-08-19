package com.oroarmor.bakedminecraftmodels.ssbo;

import java.nio.ByteBuffer;

public class SectionedPbo {
    private final ByteBuffer pointer;
    private final int name;
    private final int sectionCount;
    private final int sectionSize;
    private final long[] syncObjects;

    private int currentSection = 0;

    public SectionedPbo(ByteBuffer pointer, int name, int sectionCount, int sectionSize) {
        this.pointer = pointer;
        this.name = name;
        this.sectionCount = sectionCount;
        this.sectionSize = sectionSize;
        this.syncObjects = new long[sectionCount]; // these are 0 (null) by default
    }

    public ByteBuffer getPointer() {
        return pointer;
    }

    public int getName() {
        return name;
    }

    public int getSectionCount() {
        return sectionCount;
    }

    public int getSectionSize() {
        return sectionSize;
    }

    public long getCurrentSyncObject() {
        return syncObjects[currentSection];
    }

    public void setCurrentSyncObject(long pSyncObject) {
        syncObjects[currentSection] = pSyncObject;
    }

    public int getCurrentSection() {
        return currentSection;
    }

    public void nextSection() {
        currentSection++;
        currentSection %= sectionCount;
    }

}
