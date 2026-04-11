package com.ooad.study_buddy.focus.timer;

import com.ooad.study_buddy.focus.observer.TimerObserver;
import com.ooad.study_buddy.focus.strategy.PomodoroStrategy;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * GRASP - Information Expert: Owns all timer state and tick logic.
 * Observer Pattern - Subject: Maintains observer list and fires events.
 * SRP: Only responsible for countdown logic and notifying observers.
 */
public class PomodoroTimer {

    private final PomodoroStrategy strategy;
    private final int totalSessionSeconds;

    private final List<TimerObserver> observers = new ArrayList<>();

    private Timeline timeline;
    private int remainingInBlock;
    private int elapsedSessionSeconds;
    private boolean isFocusMode;

    public PomodoroTimer(PomodoroStrategy strategy, int totalSessionMinutes) {
        this.strategy = strategy;
        this.totalSessionSeconds = totalSessionMinutes * 60;
        this.remainingInBlock = strategy.getFocusDurationSeconds();
        this.isFocusMode = true;
        this.elapsedSessionSeconds = 0;
    }

    // ── Observer registration ────────────────────────────────────────────────

    public void addObserver(TimerObserver observer) {
        observers.add(observer);
    }

    public void removeObserver(TimerObserver observer) {
        observers.remove(observer);
    }

    // ── Playback control ─────────────────────────────────────────────────────

    public void start() {
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
        }
    }

    // ── Internal tick ────────────────────────────────────────────────────────

    private void tick() {
        elapsedSessionSeconds++;

        // Check total session expiry first
        if (elapsedSessionSeconds >= totalSessionSeconds) {
            stop();
            notifySessionEnd();
            return;
        }

        remainingInBlock--;

        if (remainingInBlock <= 0) {
            // Flip mode
            isFocusMode = !isFocusMode;
            remainingInBlock = isFocusMode
                    ? strategy.getFocusDurationSeconds()
                    : strategy.getBreakDurationSeconds();
            notifyModeChange(isFocusMode);
        }

        notifyTick(remainingInBlock);
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private void notifyTick(int remaining) {
        for (TimerObserver o : observers)
            o.onTick(remaining);
    }

    private void notifyModeChange(boolean focusMode) {
        for (TimerObserver o : observers)
            o.onModeChange(focusMode);
    }

    private void notifySessionEnd() {
        for (TimerObserver o : observers)
            o.onSessionEnd();
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public boolean isFocusMode() {
        return isFocusMode;
    }

    public int getRemainingInBlock() {
        return remainingInBlock;
    }
}