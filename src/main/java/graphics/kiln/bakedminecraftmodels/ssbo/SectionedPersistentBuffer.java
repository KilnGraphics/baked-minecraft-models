/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package graphics.kiln.bakedminecraftmodels.ssbo;

import java.util.concurrent.atomic.AtomicLong;

// TODO: abstract this but still use long for getPointer
public class SectionedPersistentBuffer {
    private final long pointer;
    private final int name;
    private final int sectionCount;
    private final long sectionSize;

    private int currentSection = 0;
    private long sectionOffset = 0;
    private AtomicLong positionOffset = new AtomicLong();

    public SectionedPersistentBuffer(long pointer, int name, int sectionCount, long sectionSize) {
        this.pointer = pointer;
        this.name = name;
        this.sectionCount = sectionCount;
        this.sectionSize = sectionSize;
    }

    public long getSectionedPointer() {
        return pointer + sectionOffset;
    }


    public int getName() {
        return name;
    }

    public long getSectionSize() {
        return sectionSize;
    }

    public int getCurrentSection() {
        return currentSection;
    }

    public void nextSection() {
        currentSection++;
        currentSection %= sectionCount;
        sectionOffset = getCurrentSection() * getSectionSize();
        positionOffset.setRelease(0);
    }

    public AtomicLong getPositionOffset() {
        return positionOffset;
    }

}
