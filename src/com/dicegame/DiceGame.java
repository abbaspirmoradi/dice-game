package com.dicegame;

/**
 * Main class to run the Dice Game.
 */
public class DiceGame {

    public static void main(String[] args) {
        // configurations
        final int numberOfDice = 2;
        final int numberOfSimulations = 10000;

        // Create an instance of the game and run it with the configured parameters.
        GameEngine engine = new GameEngine();
        engine.runEngine(numberOfDice, numberOfSimulations);
    }
}