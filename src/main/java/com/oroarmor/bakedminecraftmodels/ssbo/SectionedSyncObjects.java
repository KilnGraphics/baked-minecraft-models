package com.oroarmor.bakedminecraftmodels.ssbo;

public class SectionedSyncObjects {
    private final long[] syncObjects;

    private int currentSection = 0;

    public SectionedSyncObjects(int sectionCount) {
        this.syncObjects = new long[sectionCount]; // these are 0 (null) by default
    }

    public int getSectionCount() {
        return syncObjects.length;
    }

    public int getCurrentSection() {
        return currentSection;
    }

    public long getCurrentSyncObject() {
        return syncObjects[currentSection];
    }

    public void setCurrentSyncObject(long pSyncObject) {
        syncObjects[currentSection] = pSyncObject;
    }

    public void nextSection() {
        currentSection++;
        currentSection %= syncObjects.length;
    }
}
