package com.ooad.study_buddy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * JPA entity — one row per URL navigation event within a session.
 * SRP: Pure data holder; no logic.
 */
@Entity
@Table(name = "session_events")
public class SessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false)
    private String verdict;       // "ALLOW" | "BLOCK" | "CHECK_RELEVANCE"

    @Column
    private Double score;         // null for instant platform decisions

    @Column(length = 500)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    public SessionEvent() {}

    public SessionEvent(Long sessionId, String url,
                        String verdict, Double score,
                        String reason, LocalDateTime occurredAt) {
        this.sessionId  = sessionId;
        this.url        = url;
        this.verdict    = verdict;
        this.score      = score;
        this.reason     = reason;
        this.occurredAt = occurredAt;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Long          getId()         { return id; }
    public Long          getSessionId()  { return sessionId; }
    public String        getUrl()        { return url; }
    public String        getVerdict()    { return verdict; }
    public Double        getScore()      { return score; }
    public String        getReason()     { return reason; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
}