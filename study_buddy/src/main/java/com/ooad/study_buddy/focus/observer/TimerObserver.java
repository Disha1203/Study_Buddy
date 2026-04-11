package com.ooad.study_buddy.focus.observer;

/**
 * Observer Pattern - Observer interface.
 * SOLID ISP: Small, focused interface — observers only implement what they
 * need.
 * SOLID DIP: UI depends on this abstraction, not on PomodoroTimer directly.
 */
public interface TimerObserver {

    /**
     * Called every second with remaining seconds in the current block.
     */
    void onTick(int remainingSeconds);

    /**
     * Called when switching between FOCUS and BREAK modes.
     *
     * @param isFocus true if entering a focus block, false if entering a break.
     */
    void onModeChange(boolean isFocus);

    /**
     * Called when the total session duration has elapsed.
     */
    void onSessionEnd();
}