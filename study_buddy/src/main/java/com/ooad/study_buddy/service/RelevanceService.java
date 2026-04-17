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

/**
 * SERVICE — Deep Relevance (Phase 2)
 *
 * SRP  : Only calls the Python embedding API and maps the score to a verdict.
 * DIP  : Upper layers (RelevanceController, handlers) depend on this service
 *        interface, not on HTTP internals.
 *
 * Score thresholds (Java classification, not Python):
 *   >= 0.65  → ALLOWED
 *   0.40–0.65 → BORDERLINE
 *   <  0.40  → BLOCKED
 */
@Service
public class RelevanceService {

    private static final double THRESHOLD_RELEVANT   = 0.65;
    private static final double THRESHOLD_BORDERLINE = 0.40;

    @Value("${relevance.api.url:http://localhost:8001/relevance}")
    private String apiUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Calls the Python /relevance endpoint with the topic and combined content.
     *
     * @param topic   the study topic string
     * @param content extracted page content
     * @return RelevanceResult with verdict and score
     */
    public RelevanceResult check(String topic, ContentData content) {
        String textBlob = content.toCombinedText();

        if (textBlob.isBlank()) {
            // No content to evaluate — be permissive (could be SPA still loading)
            return RelevanceResult.borderline(0.5, "No extractable content; defaulting to borderline.");
        }

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

            JsonNode json = objectMapper.readTree(response.body());
            double score = json.get("score").asDouble();

            return classify(score, "Semantic similarity score: " + String.format("%.2f", score));

        } catch (Exception e) {
            // Network error or Python service down → be permissive to avoid
            // blocking the user when the service is unavailable
            return RelevanceResult.borderline(0.5,
                    "Relevance service unavailable: " + e.getMessage());
        }
    }

    private RelevanceResult classify(double score, String reason) {
        if (score >= THRESHOLD_RELEVANT)   return RelevanceResult.allowed(score, reason);
        if (score >= THRESHOLD_BORDERLINE) return RelevanceResult.borderline(score, reason);
        return RelevanceResult.blocked(score, reason);
    }

    // ── Inner DTO for JSON serialisation ──────────────────────────────────────

    /** Jackson POJO for the POST body. */
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
