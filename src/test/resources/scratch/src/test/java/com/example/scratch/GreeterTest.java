package com.example.scratch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Basic tests for the Greeter fixture.
 */
public class GreeterTest {

    @Test
    public void testGreet() {
        final Greeter greeter = new Greeter("world");
        assertEquals("hello, world!", greeter.greet("world"));
    }

    @Test
    public void testName() {
        assertEquals("world", new Greeter("world").name());
    }
}
