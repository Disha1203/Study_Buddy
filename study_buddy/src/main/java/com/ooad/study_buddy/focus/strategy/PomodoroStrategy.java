package com.ooad.study_buddy.focus.strategy;

/**
 * SOLID - OCP + ISP: Open for extension (new modes), minimal interface.
 * Strategy Pattern: Defines the contract all Pomodoro modes must follow.
 */
public interface PomodoroStrategy {

    /** Duration of one focus block in seconds. */
    int getFocusDurationSeconds();

    /** Duration of one break block in seconds. */
    int getBreakDurationSeconds();

    /** Human-readable label shown in the UI. */
    String getLabel();
}