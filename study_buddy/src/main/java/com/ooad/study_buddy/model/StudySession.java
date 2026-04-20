package com.ooad.study_buddy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity — one row per user focus session.
 * SRP: Pure data holder; no logic.
 */
@Entity
@Table(name = "study_sessions")
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String strategy;          // e.g. "Standard (25 / 5)"

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;    // null while session is live

    @Column(nullable = false)
    private int durationMinutes;

    public StudySession() {}

    public StudySession(String topic, String strategy,
                        LocalDateTime startedAt, int durationMinutes) {
        this.topic           = topic;
        this.strategy        = strategy;
        this.startedAt       = startedAt;
        this.durationMinutes = durationMinutes;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long          getId()              { return id; }
    public String        getTopic()           { return topic; }
    public String        getStrategy()        { return strategy; }
    public LocalDateTime getStartedAt()       { return startedAt; }
    public LocalDateTime getEndedAt()         { return endedAt; }
    public int           getDurationMinutes() { return durationMinutes; }

    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
}