package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.RelevanceResult;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * SERVICE — Session Tracking
 *
 * FIX: getLastSessionSummary() previously SELECTed without `id` in the column
 * list but called rs.getLong("id") — causing "Column 'id' not found".
 * Fixed by using a single LEFT JOIN query that selects all needed columns
 * explicitly and reads only what it selects.
 */
@Service
public class SessionTrackingService {

    private static final Logger LOG =
            Logger.getLogger(SessionTrackingService.class.getName());

    private static final String DB_URL  =
            "jdbc:mysql://localhost:3306/studybuddy" +
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123";

    private volatile Long activeSessionId = null;

    // ── Summary DTO ───────────────────────────────────────────────────────────

    public record SessionSummaryData(
            String        topic,
            String        strategyLabel,
            int           durationMinutes,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int           totalEvents,
            int           blockedEvents
    ) {}

    /**
     * Returns stats for the most recently CLOSED session (ended_at IS NOT NULL).
     *
     * FIX: One single LEFT JOIN query — reads only the columns it actually SELECTs.
     * Works even when session_events is empty (COUNT returns 0 via LEFT JOIN).
     *
     * To test manually from MySQL:
     *   SELECT s.id, s.topic, s.strategy, s.started_at, s.ended_at,
     *          s.duration_minutes,
     *          COUNT(e.id) AS total_events,
     *          SUM(CASE WHEN e.verdict='BLOCKED' THEN 1 ELSE 0 END) AS blocked_events
     *   FROM study_sessions s
     *   LEFT JOIN session_events e ON e.session_id = s.id
     *   WHERE s.ended_at IS NOT NULL
     *   GROUP BY s.id, s.topic, s.strategy, s.started_at, s.ended_at, s.duration_minutes
     *   ORDER BY s.id DESC
     *   LIMIT 1;
     */
    public SessionSummaryData getLastSessionSummary() {
        String sql =
            "SELECT " +
            "  s.topic, s.strategy, s.started_at, s.ended_at, s.duration_minutes, " +
            "  COUNT(e.id)                                              AS total_events, " +
            "  SUM(CASE WHEN e.verdict = 'BLOCKED' THEN 1 ELSE 0 END)  AS blocked_events " +
            "FROM study_sessions s " +
            "LEFT JOIN session_events e ON e.session_id = s.id " +
            "WHERE s.ended_at IS NOT NULL " +
            "GROUP BY s.topic, s.strategy, s.started_at, s.ended_at, s.duration_minutes, s.id " +
            "ORDER BY s.id DESC " +
            "LIMIT 1";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (!rs.next()) {
                LOG.info("[TRACKING] getLastSessionSummary: no closed sessions found.");
                return null;
            }

            String        topic     = rs.getString("topic");
            String        strategy  = rs.getString("strategy");
            int           duration  = rs.getInt("duration_minutes");
            LocalDateTime startedAt = rs.getTimestamp("started_at") != null
                    ? rs.getTimestamp("started_at").toLocalDateTime() : null;
            LocalDateTime endedAt   = rs.getTimestamp("ended_at") != null
                    ? rs.getTimestamp("ended_at").toLocalDateTime() : null;
            int           total     = rs.getInt("total_events");
            int           blocked   = rs.getInt("blocked_events");

            LOG.info(String.format(
                    "[TRACKING] Summary loaded — topic='%s' duration=%d total=%d blocked=%d",
                    topic, duration, total, blocked));

            return new SessionSummaryData(
                    topic, strategy, duration, startedAt, endedAt, total, blocked);

        } catch (SQLException e) {
            LOG.warning("[TRACKING] getLastSessionSummary failed (non-fatal): " + e.getMessage());
            return null;
        }
    }

    // ── Session lifecycle (UNCHANGED) ─────────────────────────────────────────

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

    // ── Event logging (UNCHANGED) ─────────────────────────────────────────────

    public void logEvent(String url, RelevanceResult result) {
        Long sid = activeSessionId;
        if (sid == null) return;
        logEventRaw(sid, url, result.getVerdict().name(),
                result.getScore(), truncate(result.getReason(), 500));
    }

    public void logPlatformDecision(String url, String verdict, String reason) {
        Long sid = activeSessionId;
        if (sid == null) return;
        logEventRaw(sid, url, verdict, null, truncate(reason, 500));
    }

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

        } catch (SQLException e) {
            LOG.warning("[TRACKING] logEvent failed (non-fatal): " + e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public Long getActiveSessionId() { return activeSessionId; }
}