package graphics.kiln.bakedminecraftmodels.data;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class AtomicAddArrayQueue<T> {
    private final AtomicInteger currentIndex = new AtomicInteger();
    private T[] elements;

    private int endSize;

    public AtomicAddArrayQueue(int initialSize) {
        elements = (T[]) new Object[initialSize];
    }

    public AtomicAddArrayQueue() {
        this(16);
    }

    public void add(T element) {
        int currentIndex = this.currentIndex.getAndIncrement();
        if (currentIndex >= elements.length - 1) {
            elements = Arrays.copyOf(elements, elements.length * 2);
        }
        elements[currentIndex] = element;
    }

    public void clear() {
        Arrays.fill(elements, null);
        endSize = currentIndex.getAndSet(0);
    }

    public T[] getArray() {
        return elements;
    }

    public int getEndSize() {
        return endSize;
    }
}
