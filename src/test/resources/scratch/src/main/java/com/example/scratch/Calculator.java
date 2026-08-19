package com.example.scratch;

/**
 * A two-operation calculator fixture.
 */
public class Calculator {

    private int lastResult;

    /**
     * Adds two numbers.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the sum
     */
    public int add(int a, int b) {
        this.lastResult = a + b;
        return this.lastResult;
    }

    /**
     * Subtracts two numbers.
     *
     * @param a the first operand
     * @param b the second operand
     * @return the difference
     */
    public int subtract(int a, int b) {
        this.lastResult = a - b;
        return this.lastResult;
    }

    /**
     * @return the result of the most recent operation
     */
    public int lastResult() {
        return this.lastResult;
    }
}
