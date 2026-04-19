package com.ooad.study_buddy.focus.strategy;

/**
 * DEV-ONLY strategy: 10-second focus / 10-second break.
 * Use this to test break-mode blocking without waiting 25 minutes.
 * Remove from STRATEGIES[] before shipping.
 */
public class Dev1010Strategy implements PomodoroStrategy {

    @Override
    public int getFocusDurationSeconds() { return 20; }

    @Override
    public int getBreakDurationSeconds()  { return 30; }

    @Override
    public String getLabel() { return "DEV (10s / 10s)"; }
}