package com.ooad.study_buddy.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * SERVICE — Deep Relevance (Phase 2)
 *
 * Calls the Python embedding API and maps the returned cosine score
 * to an ALLOWED / BORDERLINE / BLOCKED verdict.
 *
 * Uses HttpURLConnection instead of HttpClient to avoid body-encoding
 * issues that caused FastAPI to receive null body (HTTP 422).
 */
@Service
public class RelevanceService {

    private static final Logger LOG = Logger.getLogger(RelevanceService.class.getName());

    // ── Thresholds ────────────────────────────────────────────────────────────
    private static final double THRESHOLD_ALLOWED    = 0.55;
    private static final double THRESHOLD_BORDERLINE = 0.35;

    // Field initializer acts as fallback when Spring DI is not running (e.g. in probe)
    @Value("${relevance.api.url:http://localhost:8001/relevance}")
    private String apiUrl = "http://localhost:8001/relevance";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calls the Python /relevance endpoint.
     *
     * @param topic   the study topic string
     * @param content extracted page content
     * @return RelevanceResult with verdict and score
     */
    public RelevanceResult check(String topic, ContentData content) {
        String textBlob = content.toCombinedText();

        if (textBlob == null || textBlob.isBlank()) {
            LOG.warning("[RELEVANCE-SVC] Empty content for: " + content.getUrl()
                    + " — defaulting to BLOCKED");
            return RelevanceResult.blocked(0.0,
                    "No extractable content from page.");
        }

        LOG.info(String.format("[RELEVANCE-SVC] Calling Python API | topic='%s' | contentLen=%d | url=%s",
                topic, textBlob.length(), content.getUrl()));

        try {
            String body = objectMapper.writeValueAsString(
                    new RelevanceRequest(topic, textBlob));

            System.out.println("[DEBUG] Sending JSON: " + body);

            // ── HTTP call via HttpURLConnection ───────────────────────────────
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            // Write body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            int status = conn.getResponseCode();
            System.out.println("[DEBUG] HTTP status: " + status);

            // ── Error response ────────────────────────────────────────────────
            if (status != 200) {
                String errorBody = new String(
                        conn.getErrorStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                LOG.warning("[RELEVANCE-SVC] Python API returned HTTP "
                        + status + " for " + content.getUrl());
                LOG.warning("[RELEVANCE-SVC] Response body: " + errorBody);
                return fallbackResult("Python API HTTP " + status);
            }

            // ── Success response ──────────────────────────────────────────────
            String responseBody = new String(
                    conn.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);

            JsonNode json = objectMapper.readTree(responseBody);
            double score  = json.get("score").asDouble();

            LOG.info(String.format("[RELEVANCE-SVC] score=%.4f for url=%s",
                    score, content.getUrl()));

            return classify(score,
                    String.format("Semantic similarity score: %.4f", score));

        } catch (java.net.ConnectException e) {
            LOG.severe("[RELEVANCE-SVC] Python service unreachable at " + apiUrl
                    + " — blocking by default. Start: uvicorn relevance_api:app --port 8001");
            return fallbackResult("Python relevance service unreachable.");

        } catch (Exception e) {
            LOG.warning("[RELEVANCE-SVC] Unexpected error: " + e.getMessage());
            return fallbackResult("Relevance check error: " + e.getMessage());
        }
    }

    // ── Classification ────────────────────────────────────────────────────────

    private RelevanceResult classify(double score, String reason) {
        if (score >= THRESHOLD_ALLOWED)    return RelevanceResult.allowed(score, reason);
        if (score >= THRESHOLD_BORDERLINE) return RelevanceResult.borderline(score, reason);
        return RelevanceResult.blocked(score, reason);
    }

    /**
     * Fallback when Python is unreachable.
     * Returns BLOCKED so a dead Python service doesn't let all pages through.
     */
    private RelevanceResult fallbackResult(String reason) {
        return RelevanceResult.blocked(0.0,
                "Relevance service unavailable — blocking by default. " + reason);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    private static class RelevanceRequest {

        @JsonProperty("topic")
        public final String topic;

        @JsonProperty("content")
        public final String content;

        RelevanceRequest(String topic, String content) {
            this.topic   = topic;
            this.content = content;
        }
    }
}