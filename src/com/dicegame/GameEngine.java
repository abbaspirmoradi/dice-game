package com.dicegame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * This code plays the dice game and keeps a record of every score.
 * It's also designed to be flexible and easily change the number of dice or the number of games,
 * which means the same code can be reused for many different tests.
 */
public class GameEngine {

    // Final constants for internal game rules that don't change
    private static final int VALUE_TO_REMOVE = 3;

    // A map to store the count of each score.
    private final Map<Integer, Integer> scoreCounts;

    public GameEngine() {
        this.scoreCounts = new HashMap<>();
    }

    /**
     * Runs the engine for a specified number of times with a given number of dice.
     * @param numberOfDice The number of dice to use in each game.
     * @param numberOfSimulations The total number of games to simulate.
     */
    public void runEngine(int numberOfDice, int numberOfSimulations) {
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < numberOfSimulations; i++) {
            int gameScore = playOneGame(numberOfDice);
            // Increment the count for the game score.
            scoreCounts.put(gameScore, scoreCounts.getOrDefault(gameScore, 0) + 1);
        }

        long endTime = System.currentTimeMillis();
        double durationInSeconds = (endTime - startTime) / 1000.0;
        printResults(numberOfDice, numberOfSimulations, durationInSeconds);
    }

    /**
     * Plays a single game of the dice game from start to finish.
     * @param numberOfDice The number of dice for this game.
     * @return The total accumulated score for the game.
     */
    private int playOneGame(int numberOfDice) {
        int totalScore = 0;

        List<Integer> diceOnBoard = initializeDice(numberOfDice);

        while (!diceOnBoard.isEmpty()) {
            int scoreForRoll = calculateRollScore(diceOnBoard);
            totalScore += scoreForRoll;
        }

        return totalScore;
    }

    /**
     * Initializes the dice for the start of a new game by rolling each die once.
     * @param numberOfDice The number of dice to roll.
     * @return A new list containing the values of the rolled dice.
     */
    private List<Integer> initializeDice(int numberOfDice) {
        List<Integer> diceValues = new ArrayList<>();
        Dice diceRoller = new Dice();
        for (int i = 0; i < numberOfDice; i++) {
            diceValues.add(diceRoller.roll());
        }
        return diceValues;
    }

    /**
     * Calculates the score for a single roll and updates the dice on the board.
     * This method applies the game's core scoring logic.
     * @param diceOnBoard The list of dice currently on the board.
     * @return The score awarded for this specific roll.
     */
    private int calculateRollScore(List<Integer> diceOnBoard) {
        // Step 1: Check for all 3.
        if (diceOnBoard.contains(VALUE_TO_REMOVE)) {
            removeMatchingDice(diceOnBoard, VALUE_TO_REMOVE);
            return 0; // The score is 0 if any 3 are present.
        } else {
            // Step 2: If no 3, find and remove the lowest die.
            int lowestDieValue = findLowestDie(diceOnBoard);
            removeMatchingDice(diceOnBoard, lowestDieValue);
            return lowestDieValue; // The score is the value of the lowest die.
        }
    }

    /**
     * Finds the lowest value among the dice on the board.
     * @param dice The list of dice values.
     * @return The lowest value.
     */
    private int findLowestDie(List<Integer> dice) {
        // find the minimum value
        int lowestValue = Integer.MAX_VALUE;
        for (int dieValue : dice) {
            if (dieValue < lowestValue) {
                lowestValue = dieValue;
            }
        }
        return lowestValue;
    }

    /**
     * Removes all dice with a specific value from the board.
     * @param dice The list of dice to modify.
     * @param valueToRemove The value to be removed.
     */
    private void removeMatchingDice(List<Integer> dice, int valueToRemove) {
        for (int i = dice.size() - 1; i >= 0; i--) {
            if (dice.get(i) == valueToRemove) {
                dice.remove(i);
            }
        }
    }

    /**
     * Prints the final results of the simulation.
     * @param numberOfDice The number of dice used in the simulation.
     * @param numberOfSimulations The total number of simulations run.
     * @param duration The total duration of the simulation in seconds.
     */
    private void printResults(int numberOfDice, int numberOfSimulations, double duration) {
        System.out.println("Number of simulations was " + numberOfSimulations + " using " + numberOfDice + " dice.");
        System.out.println("------------------------------------------------------------------");

        List<Integer> sortedScores = new ArrayList<>(scoreCounts.keySet());
        Collections.sort(sortedScores);

        for (int score : sortedScores) {
            int count = scoreCounts.get(score);
            double probability = (double) count / numberOfSimulations;
            System.out.printf("Total %d occurs %.2f occurred %d times.\n", score, probability, count);
        }

        System.out.println("------------------------------------------------------------------");
        System.out.printf("Total simulation took %.2f seconds.\n", duration);
    }
}