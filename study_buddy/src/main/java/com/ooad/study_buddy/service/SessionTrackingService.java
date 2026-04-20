package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.model.SessionEvent;
import com.ooad.study_buddy.model.StudySession;
import com.ooad.study_buddy.repository.SessionEventRepository;
import com.ooad.study_buddy.repository.StudySessionRepository;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * SERVICE — Session Tracking
 *
 * SRP: Only responsible for persisting session lifecycle and navigation events.
 * DIP: Depends only on repositories (abstractions), not on UI or other services.
 * Safe: All DB writes are wrapped in try/catch — failures never crash the app.
 *
 * Integration points (called from BrowserController and BrowserLauncher):
 *   openSession()  — call when user starts a session
 *   closeSession() — call when session timer ends or user goes back
 *   logEvent()     — call after every relevance/blocking decision
 */
@Service
public class SessionTrackingService {

    private static final Logger LOG =
            Logger.getLogger(SessionTrackingService.class.getName());

    // ── Direct JDBC (mirrors DatabaseSeedService pattern) ─────────────────────
    // We use JDBC instead of Spring JPA because BrowserLauncher constructs
    // services manually outside the Spring ApplicationContext.
    private static final String DB_URL  =
            "jdbc:mysql://localhost:3306/studybuddy" +
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123";

    /** The DB-assigned id of the currently open session; null when idle. */
    private volatile Long activeSessionId = null;

    // ── Session lifecycle ─────────────────────────────────────────────────────

    /**
     * Opens a new session row in study_sessions.
     * Call once when the user clicks "Start Session".
     *
     * @param topic           the validated topic string
     * @param strategyLabel   e.g. "Standard (25 / 5)"
     * @param durationMinutes total session duration the user requested
     */
    public void openSession(String topic, String strategyLabel, int durationMinutes) {
        String sql =
            "INSERT INTO study_sessions (topic, strategy, started_at, duration_minutes) " +
            "VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, topic);
            ps.setString(2, strategyLabel);
            ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
            ps.setInt(4, durationMinutes);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    activeSessionId = keys.getLong(1);
                    LOG.info("[TRACKING] Session opened: id=" + activeSessionId
                            + " topic='" + topic + "'");
                }
            }
        } catch (SQLException e) {
            LOG.warning("[TRACKING] openSession failed (non-fatal): " + e.getMessage());
        }
    }

    /**
     * Closes the active session by writing ended_at.
     * Safe to call multiple times (subsequent calls are no-ops).
     */
    public void closeSession() {
        Long sid = activeSessionId;
        if (sid == null) return;

        String sql = "UPDATE study_sessions SET ended_at = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(2, sid);
            ps.executeUpdate();
            LOG.info("[TRACKING] Session closed: id=" + sid);

        } catch (SQLException e) {
            LOG.warning("[TRACKING] closeSession failed (non-fatal): " + e.getMessage());
        } finally {
            activeSessionId = null;
        }
    }

    // ── Event logging ─────────────────────────────────────────────────────────

    /**
     * Logs one navigation event (URL + decision) against the active session.
     * If no session is open, the call is silently ignored.
     *
     * @param url    the full URL that was navigated to
     * @param result the RelevanceResult produced by the pipeline
     */
    public void logEvent(String url, RelevanceResult result) {
        Long sid = activeSessionId;
        if (sid == null) return;   // no active session — skip silently

        String verdict = result.getVerdict().name();  // "ALLOWED" | "BLOCKED" | "BORDERLINE"
        double score   = result.getScore();
        String reason  = truncate(result.getReason(), 500);

        logEventRaw(sid, url, verdict, score, reason);
    }

    /**
     * Logs a platform-level decision (ALLOW/BLOCK by BlockingService)
     * where no RelevanceResult exists.
     *
     * @param url     the URL
     * @param verdict "ALLOW" or "BLOCK"
     * @param reason  short human-readable reason
     */
    public void logPlatformDecision(String url, String verdict, String reason) {
        Long sid = activeSessionId;
        if (sid == null) return;
        logEventRaw(sid, url, verdict, null, truncate(reason, 500));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void logEventRaw(Long sessionId, String url,
                              String verdict, Double score, String reason) {
        String sql =
            "INSERT INTO session_events " +
            "(session_id, url, verdict, score, reason, occurred_at) " +
            "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, sessionId);
            ps.setString(2, truncate(url, 2048));
            ps.setString(3, verdict);
            if (score != null) ps.setDouble(4, score);
            else               ps.setNull(4, Types.DOUBLE);
            ps.setString(5, reason);
            ps.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();

            LOG.fine("[TRACKING] Event logged: verdict=" + verdict
                    + " url=" + url);

        } catch (SQLException e) {
            LOG.warning("[TRACKING] logEvent failed (non-fatal): " + e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Exposed for diagnostics / testing. */
    public Long getActiveSessionId() { return activeSessionId; }
}