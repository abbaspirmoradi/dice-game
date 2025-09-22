package com.dicegame;

import java.util.Random;

/**
 * Represents a single six-sided die.
 */
public class Dice {

    private static final int SIDES = 6;
    private final Random randomGenerator;

    /**
     * Constructs a new Dice object.
     */
    public Dice() {
        this.randomGenerator = new Random();
    }

    /**
     * Rolls the die and returns a random value between 1 and 6.
     * @return The value of the rolled die.
     */
    public int roll() {
        return randomGenerator.nextInt(SIDES) + 1;
    }
}