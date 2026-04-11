package com.ooad.study_buddy.focus.strategy;

/**
 * Strategy Pattern - Concrete Strategy: Classic 25-minute focus / 5-minute
 * break.
 * SOLID LSP: Fully substitutable for any PomodoroStrategy reference.
 */
public class Standard2505Strategy implements PomodoroStrategy {

    @Override
    public int getFocusDurationSeconds() {
        return 25 * 60;
    }

    @Override
    public int getBreakDurationSeconds() {
        return 5 * 60;
    }

    @Override
    public String getLabel() {
        return "Standard (25 / 5)";
    }
}