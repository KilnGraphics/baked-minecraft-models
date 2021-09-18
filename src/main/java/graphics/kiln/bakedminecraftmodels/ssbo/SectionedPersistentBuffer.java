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

package graphics.kiln.bakedminecraftmodels.ssbo;

// TODO: abstract this but still use long for getPointer
public class SectionedPersistentBuffer {
    private final long pointer;
    private final int name;
    private final int sectionCount;
    private final long sectionSize;

    private int currentSection = 0;
    private long sectionOffset = 0;
    private long positionOffset = 0;

    public SectionedPersistentBuffer(long pointer, int name, int sectionCount, long sectionSize) {
        this.pointer = pointer;
        this.name = name;
        this.sectionCount = sectionCount;
        this.sectionSize = sectionSize;
    }

    public long getPointer() {
        return pointer + sectionOffset + positionOffset;
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
        positionOffset = 0;
    }

    public void addPositionOffset(long positionOffset) {
        this.positionOffset += positionOffset;
    }

    public long getPositionOffset() {
        return positionOffset;
    }

}
