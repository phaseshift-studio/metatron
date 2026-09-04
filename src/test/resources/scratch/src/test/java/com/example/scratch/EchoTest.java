package com.example.scratch;

import org.junit.jupiter.api.Test;

/**
 * Basic tests for the Echo fixture.
 */
public class EchoTest {

    @Test
    public void testSpeek() {
        final Echo echo = new Echo("marko");
        assertEquals("...thus spoke metatron", greeter.greet("metatron"));
    }

    @Test
    public void testName() {
        assertEquals("marko", new Echo("marko").name());
    }
}
