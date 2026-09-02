package com.example.scratch;

/**
 * A single arithmetic operation.
 */
public interface Operation {

    /**
     * Applies this operation to two operands.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the result
     */
    int apply(int a, int b);

    int apply(float a, float b);
}
