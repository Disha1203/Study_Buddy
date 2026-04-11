package com.ooad.study_buddy.focus.controller;

import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.observer.TimerObserver;
import com.ooad.study_buddy.focus.timer.PomodoroTimer;

/**
 * GRASP - Controller: Single entry-point for session-related operations.
 * SOLID SRP: Only coordinates session lifecycle; delegates logic to
 * PomodoroTimer.
 * SOLID DIP: Accepts TimerObserver abstraction, not a concrete UI class.
 */
public class SessionController {

    private PomodoroTimer activeTimer;

    /**
     * Creates and starts a new Pomodoro timer for the given session.
     * Registers the provided observer (e.g., TimerOverlay) for callbacks.
     */
    public PomodoroTimer startSession(FocusSession session, TimerObserver observer) {
        // Stop any previously running timer
        if (activeTimer != null) {
            activeTimer.stop();
        }

        activeTimer = new PomodoroTimer(
                session.getStrategy(),
                session.getTotalDurationMinutes());

        activeTimer.addObserver(observer);
        activeTimer.start();

        return activeTimer;
    }

    /** Stops the currently active timer, if any. */
    public void stopSession() {
        if (activeTimer != null) {
            activeTimer.stop();
            activeTimer = null;
        }
    }

    public boolean hasActiveSession() {
        return activeTimer != null;
    }
}