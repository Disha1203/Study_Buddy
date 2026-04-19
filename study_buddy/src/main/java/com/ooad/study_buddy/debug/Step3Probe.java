package com.ooad.study_buddy.debug;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.service.ContentExtractionService;
import javafx.application.Application;
import javafx.concurrent.Worker;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

/**
 * STEP 3 PROBE — Content Extraction Tester
 *
 * Tests ContentExtractionService across multiple real-world URLs.
 * Helps debug:
 *  - Missing metadata
 *  - JS-heavy pages
 *  - Null safety
 */
public class Step3Probe extends Application {

    @Override
    public void start(Stage stage) {

        WebView webView = new WebView();
        WebEngine engine = webView.getEngine();
        ContentExtractionService svc = new ContentExtractionService();

        // ── Test URLs ─────────────────────────────────────────────────────────
        String[] urls = {

                // ───── CLEAN EDUCATIONAL ─────
                "https://en.wikipedia.org/wiki/Binary_search_tree",
                "https://www.geeksforgeeks.org/binary-search-tree-data-structure/",
                "https://cp-algorithms.com/data_structures/segment_tree.html",

                // ───── BLOG / ARTICLE ─────
                "https://towardsdatascience.com/what-is-machine-learning-5ed3f8a4d9f3",

                // ───── JS HEAVY (SPA) ─────
                "https://leetcode.com/problems/binary-search/",
                "https://www.youtube.com/watch?v=0JUN9aDxVmI",

                // ───── LOW CONTENT ─────
                "https://example.com",
                "https://httpbin.org/html",

                // ───── RESTRICTED / LOGIN ─────
                "https://www.linkedin.com/feed/",
                "https://www.instagram.com/",

                // ───── DISCUSSION ─────
                "https://reddit.com/r/learnprogramming/",
                "https://www.reddit.com/r/MachineLearning/comments/1g9o9x3/how_do_we_move_beyond_neural_networks_discussion/",

                // ───── EDGE CASE ─────
                "about:blank"
        };

        int[] index = {0};

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {

            if (newState == Worker.State.SUCCEEDED) {

                String currentUrl = engine.getLocation();

                System.out.println("\n========================================");
                System.out.println("🌐 URL: " + currentUrl);

                ContentData data = svc.extract(engine, currentUrl);

                // ── Safe printing ────────────────────────────────────────────
                System.out.println("title       : " + safe(data.getTitle()));
                System.out.println("ogTitle     : " + safe(data.getOpenGraphTitle()));
                System.out.println("metaDesc    : " + safe(data.getMetaDescription()));
                System.out.println("firstH1     : " + safe(data.getFirstHeading()));

                String combined = data.toCombinedText();

                if (combined == null || combined.isBlank()) {
                    System.out.println("combined    : EMPTY");
                } else {
                    System.out.println("combined    : " +
                            combined.substring(0, Math.min(120, combined.length())));
                    System.out.println("text length : " + combined.length());
                }

                // ── Load next URL ────────────────────────────────────────────
                index[0]++;
                if (index[0] < urls.length) {
                    engine.load(urls[index[0]]);
                } else {
                    System.out.println("\n✅ All test cases completed.");
                }
            }
        });

        // Start first URL
        engine.load(urls[0]);

        stage.setScene(new Scene(webView, 1000, 700));
        stage.setTitle("Step3Probe — Content Extraction Tester");
        stage.show();
    }

    // ── Helper for null-safe printing ─────────────────────────────────────────

    private String safe(String value) {
        return (value == null || value.isBlank()) ? "NULL" : value;
    }

    public static void main(String[] args) {
        launch();
    }
}