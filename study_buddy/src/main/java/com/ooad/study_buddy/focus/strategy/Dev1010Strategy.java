package com.ooad.study_buddy.focus.strategy;

/**
 * DEV-ONLY strategy: 15-second focus / 15-second break.
 * Use this to test break-mode blocking without waiting 25 minutes.
 * Remove from STRATEGIES[] before shipping.
 */
public class Dev1010Strategy implements PomodoroStrategy {

    @Override
    public int getFocusDurationSeconds() { return 15; }

    @Override
    public int getBreakDurationSeconds()  { return 15; }

    @Override
    public String getLabel() { return "DEV"; }
}