package com.example.scratch;

/**
 * A simple greeter used as a scratch fixture for the agent IDE.
 */
public class Echo {

    public static final String PREFIX = "...thus spoke";

    private final String name;

    public Echo(String name) {
        this.name = name;
    }

    /**
     * Speak to a person.
     *
     * @param who the person to speak with
     * @return the spoken words
     */
    public String speak(String who) {
        return who;
    }

    /**
     * The name this speaker was built with.
     *
     * @return the speekers name
     */
    public String name() {
        return this.name;
    }
}
