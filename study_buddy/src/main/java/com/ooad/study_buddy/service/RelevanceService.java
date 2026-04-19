package com.ooad.study_buddy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * SERVICE — Deep Relevance (Phase 2)
 *
 * Calls the Python embedding API and maps the returned cosine score
 * to an ALLOWED / BORDERLINE / BLOCKED verdict.
 *
 * ══════════════════════════════════════════════════════════════
 *  BUG FIX — Thresholds recalibrated for raw cosine similarity
 * ══════════════════════════════════════════════════════════════
 *
 *  PROBLEM:
 *    The Python API was normalizing scores via (raw + 1) / 2, which
 *    compressed real cosine values into [0.55, 0.95]. This meant
 *    "watermelon" vs "malware analysis" scored 0.56 — right at the
 *    allow threshold — causing clearly unrelated pages to slip through.
 *
 *    With the Python fix (raw cosine clamped to [0,1]):
 *      - "watermelon" vs "malware analysis"  → raw ≈ 0.13
 *      - "PE header for malware"              → raw ≈ 0.60
 *      - "static analysis with IDA Pro"       → raw ≈ 0.72
 *
 *  OLD thresholds (calibrated for the distorted (x+1)/2 output):
 *    ALLOWED   >= 0.65
 *    BORDERLINE  0.40–0.65
 *    BLOCKED   <  0.40
 *
 *  NEW thresholds (calibrated for raw cosine output):
 *    ALLOWED   >= 0.55   ← lowered because raw scores are naturally lower
 *    BORDERLINE  0.35–0.55
 *    BLOCKED   <  0.35   ← "watermelon" at 0.13 is well below this
 *
 *  REASONING:
 *    With all-MiniLM-L6-v2, empirically:
 *      - Clearly related pairs:     raw cosine ≈ 0.55–0.90
 *      - Loosely related pairs:     raw cosine ≈ 0.35–0.55
 *      - Unrelated pairs:           raw cosine ≈ 0.05–0.25
 *    Setting ALLOW at 0.55 means only content with genuine semantic
 *    overlap is let through. 0.35–0.55 is borderline (promoted to
 *    BLOCKED by RelevanceController). Below 0.35 is clearly off-topic.
 *
 *  RESILIENCE:
 *    If the Python service is unreachable, the fallback is now
 *    BORDERLINE(0.30) — below the new BORDERLINE floor of 0.35 —
 *    so it gets promoted to BLOCKED by RelevanceController.
 *    Previously the fallback was BORDERLINE(0.5) which was above the
 *    old BORDERLINE floor and was therefore ALLOWED, meaning a dead
 *    Python service let ALL pages through.
 *
 * ══════════════════════════════════════════════════════════════
 */
@Service
public class RelevanceService {

    private static final Logger LOG = Logger.getLogger(RelevanceService.class.getName());

    // ── Thresholds (calibrated for RAW cosine similarity from Python v1.1) ────
    // These match the /debug endpoint output with the fixed normalization.
    private static final double THRESHOLD_ALLOWED    = 0.55;  // was 0.65
    private static final double THRESHOLD_BORDERLINE = 0.35;  // was 0.40

    @Value("${relevance.api.url:http://localhost:8001/relevance}")
    private String apiUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

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
            // No content extracted (paywall / CSP / SPA not loaded)
            // Return a low score → will be BLOCKED by RelevanceController
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.warning("[RELEVANCE-SVC] Python API returned HTTP "
                        + response.statusCode() + " for " + content.getUrl());
                return fallbackResult("Python API HTTP " + response.statusCode());
            }

            JsonNode json  = objectMapper.readTree(response.body());
            double score   = json.get("score").asDouble();

            LOG.info(String.format("[RELEVANCE-SVC] score=%.4f for url=%s",
                    score, content.getUrl()));

            return classify(score,
                    String.format("Semantic similarity score: %.4f", score));

        } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException e) {
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
     *
     * FIX: Returns score=0.0 (BLOCKED) instead of the old BORDERLINE(0.5).
     * With the old code, a dead Python service let ALL pages through because
     * BORDERLINE(0.5) > old block threshold(0.40) and isBlocked()=false.
     * Now: score 0.0 → BLOCKED immediately, no ambiguity.
     */
    private RelevanceResult fallbackResult(String reason) {
        return RelevanceResult.blocked(0.0,
                "Relevance service unavailable — blocking by default. " + reason);
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    private static class RelevanceRequest {
        public final String topic;
        public final String content;
        RelevanceRequest(String topic, String content) {
            this.topic   = topic;
            this.content = content;
        }
    }
}