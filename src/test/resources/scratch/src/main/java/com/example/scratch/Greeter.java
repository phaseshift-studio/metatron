package com.example.scratch;

/**
 * A simple greeter used as a scratch fixture for the agent IDE.
 */
public class Greeter {

    public static final String GREETING = "hello";

    private final String name;

    public Greeter(String name) {
        this.name = name;
    }

    /**
     * Greets a person.
     *
     * @param who the person to greet
     * @return the greeting
     */
    public String greet(String who) {
        return GREETING + ", " + who + "!";
    }

    /**
     * The name this greeter was built with.
     *
     * @return the greeter name
     */
    public String name() {
        return this.name;
    }
}
