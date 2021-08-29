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
