/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.ssbo;

public class SectionedSyncObjects {
    private final long[] syncObjects;

    private int currentSection = 0;

    public SectionedSyncObjects(int sectionCount) {
        this.syncObjects = new long[sectionCount]; // these are 0 (null) by default
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
