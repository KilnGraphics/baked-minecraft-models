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

package com.oroarmor.bakedminecraftmodels.data;

import net.minecraft.util.math.Matrix4f;

public class MatrixList {

    private Node first;
    private Node current;
    private Node last;
    private int largestIndex;

    /**
     * Adds a node with the specified properties to the end of the list
     */
    public void add(int index, Matrix4f matrix) {
        if (index > largestIndex) {
            largestIndex = index;
        }

        Node newNode = new Node(index, matrix);
        if (last != null) {
            last.next = newNode;
        }
        last = newNode;
        if (first == null) {
            first = newNode;
            current = newNode;
        }
    }

    /**
     * Advances the current node to the next in the list.
     * @return the previous node, or null if there are no remaining nodes.
     */
    public Node next() {
        Node old = current;
        if (current != null) {
            current = old.next;
        }
        return old;
    }

    /**
     * Sets the current node to the first in the list.
     */
    public void reset() {
        current = first;
    }

    public int getLargestIndex() {
        return largestIndex;
    }

    /**
     * Removes all nodes in the list and resets the largestIndex.
     */
    public void clear() {
        first = null;
        last = null;
        // The java LinkedList class clears all the references inside the nodes, but it likely isn't needed for our case.
        largestIndex = 0;
    }

    public static class Node {
        private Node next;

        private final int index;
        private final Matrix4f matrix;

        private Node(int index, Matrix4f matrix) {
            this.index = index;
            this.matrix = matrix;
        }

        public int getIndex() {
            return index;
        }

        public Matrix4f getMatrix() {
            return matrix;
        }
    }
}
