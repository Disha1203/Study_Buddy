package com.ooad.study_buddy.focus.strategy;

/**
 * Strategy Pattern - Concrete Strategy: 50-minute focus / 10-minute break.
 * SOLID OCP: Added without modifying any existing class.
 */
public class Extended5010Strategy implements PomodoroStrategy {

    @Override
    public int getFocusDurationSeconds() {
        return 50 * 60;
    }

    @Override
    public int getBreakDurationSeconds() {
        return 10 * 60;
    }

    @Override
    public String getLabel() {
        return "Extended (50 / 10)";
    }
}