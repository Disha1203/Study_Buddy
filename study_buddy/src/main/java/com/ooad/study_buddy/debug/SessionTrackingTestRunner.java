package com.ooad.study_buddy.debug;

import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.service.SessionTrackingService;

public class SessionTrackingTestRunner {

    public static void main(String[] args) throws InterruptedException {

        SessionTrackingService tracking = new SessionTrackingService();

        System.out.println("=== TEST START ===");

        // ── 1. OPEN SESSION ───────────────────────────────────────────
        tracking.openSession("Dijkstra Algorithm", "Standard (25/5)", 25);

        Thread.sleep(1000); // simulate time gap

        // ── 2. LOG PLATFORM DECISION ─────────────────────────────────
        tracking.logPlatformDecision(
                "https://www.google.com",
                "ALLOW",
                "homepage allowed"
        );

        Thread.sleep(1000);

        // ── 3. LOG RELEVANCE RESULT ──────────────────────────────────
        RelevanceResult relevant =
                RelevanceResult.allowed(0.85, "Highly relevant content");

        tracking.logEvent("https://example.com/dijkstra", relevant);

        Thread.sleep(1000);

        // ── 4. LOG BLOCKED RESULT ────────────────────────────────────
        RelevanceResult blocked =
                RelevanceResult.blocked(0.2, "Irrelevant content");

        tracking.logEvent("https://youtube.com/shorts/abc", blocked);

        Thread.sleep(1000);

        // ── 5. CLOSE SESSION ─────────────────────────────────────────
        tracking.closeSession();

        System.out.println("=== TEST COMPLETE ===");
    }
}