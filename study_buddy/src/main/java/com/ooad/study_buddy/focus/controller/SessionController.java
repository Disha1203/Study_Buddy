package com.ooad.study_buddy.focus.controller;

import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.observer.TimerObserver;
import com.ooad.study_buddy.focus.timer.PomodoroTimer;
import com.ooad.study_buddy.focus.ui.TimerOverlay;

/**
 * GRASP - Controller: Single entry-point for session-related operations.
 */
public class SessionController {

    private PomodoroTimer activeTimer;

    public PomodoroTimer startSession(FocusSession session, TimerObserver observer) {
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