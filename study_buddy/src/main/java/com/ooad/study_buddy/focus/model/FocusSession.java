package com.ooad.study_buddy.focus.model;

import com.ooad.study_buddy.focus.strategy.PomodoroStrategy;

/**
 * GRASP - Information Expert: Holds all data about a focus session.
 * SRP: Only stores session state, no logic.
 */
public class FocusSession {

    private final String topic;
    private final int totalDurationMinutes;
    private final PomodoroStrategy strategy;

    public FocusSession(String topic, int totalDurationMinutes, PomodoroStrategy strategy) {
        this.topic = topic;
        this.totalDurationMinutes = totalDurationMinutes;
        this.strategy = strategy;
    }

    public String getTopic() {
        return topic;
    }

    public int getTotalDurationMinutes() {
        return totalDurationMinutes;
    }

    public PomodoroStrategy getStrategy() {
        return strategy;
    }
}