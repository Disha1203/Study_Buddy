package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.focus.ui.BlockPageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import com.ooad.study_buddy.model.RelevanceResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * VIEW — AI-Relevance Browser
 *
 * SRP  : Owns browser UI layout and navigation bar. Zero business logic.
 * DIP  : Accepts BrowserController (abstraction); never calls services directly.
 *
 * This replaces / extends the original BrowserView while keeping the same
 * public API so BrowserLauncher needs only minor changes.
 */
public class AiBrowserView {

    // ── JavaFX widgets ────────────────────────────────────────────────────────
    private final WebView   webView;
    private final WebEngine webEngine;
    private final TextField urlField;

    // ── Navigation history (back button) ─────────────────────────────────────
    private final Deque<String> history = new ArrayDeque<>();
    private String currentUrl = null;

    // ── Overlay / block page references ──────────────────────────────────────
    private AnchorPane contentPane;
    private TimerOverlay timerOverlay;
    private BlockPageView blockPageView;

    // ── Scene-swap callback: called with blocked URL + result ─────────────────
    private java.util.function.BiConsumer<String, RelevanceResult> onBlocked;

    public AiBrowserView() {
        webView   = new WebView();
        webEngine = webView.getEngine();

        urlField = new TextField();
        urlField.setPromptText("Search or enter URL");
        urlField.setPrefHeight(36);
        urlField.setStyle(
                "-fx-background-radius: 8;" +
                "-fx-border-radius: 8;" +
                "-fx-padding: 8;" +
                "-fx-background-color: #2c2c3c;" +
                "-fx-text-fill: white;" +
                "-fx-prompt-text-fill: #aaaaaa;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        // Keep URL bar in sync with navigations (back/forward, link clicks)
        webEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                Platform.runLater(() -> urlField.setText(newVal));
            }
        });
    }

    // ── Public builder ────────────────────────────────────────────────────────

    /**
     * Builds the full browser layout and attaches the relevance controller.
     *
     * @param overlay           optional Pomodoro overlay (may be null)
     * @param browserController wired relevance controller
     * @param topic             active study topic
     * @return ready-to-display BorderPane
     */
    public BorderPane getView(TimerOverlay overlay,
                              BrowserController browserController,
                              String topic) {
        this.timerOverlay = overlay;
        this.blockPageView = new BlockPageView();

        // Navigation bar
        BorderPane root = new BorderPane();
        root.setTop(buildNavBar());

        // Content pane: WebView + optional overlay, all in an AnchorPane
        contentPane = new AnchorPane();
        webView.prefWidthProperty().bind(contentPane.widthProperty());
        webView.prefHeightProperty().bind(contentPane.heightProperty());
        contentPane.getChildren().add(webView);

        if (overlay != null) {
            overlay.setMaxWidth(160);
            overlay.setPrefWidth(160);
            AnchorPane.setBottomAnchor(overlay, 16.0);
            AnchorPane.setRightAnchor(overlay, 16.0);
            contentPane.getChildren().add(overlay);
        }

        root.setCenter(contentPane);
        root.setStyle("-fx-background-color: #0f0f0f;");

        // Wire relevance checking
        browserController.attach(webEngine, topic, (url, result) ->
                Platform.runLater(() -> handleRelevanceResult(url, result, root)));

        return root;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void loadUrl(String url) {
        if (currentUrl != null) history.push(currentUrl);
        currentUrl = url;
        String full = url.startsWith("http") ? url : "https://" + url;
        webEngine.load(full);
    }

    public void goBack() {
        if (!history.isEmpty()) {
            currentUrl = history.pop();
            webEngine.load(currentUrl);
        }
    }

    // ── Relevance result handler ──────────────────────────────────────────────

    private void handleRelevanceResult(String url,
                                       RelevanceResult result,
                                       BorderPane root) {
        if (result.isBlocked()) {
            // Swap center to block page
            BorderPane blockPage = blockPageView.getView(result, url, () -> {
                // "Go Back": restore webview and navigate back
                root.setCenter(contentPane);
                goBack();
            });
            root.setCenter(blockPage);
        } else {
            // Ensure webview is showing (in case we previously blocked)
            root.setCenter(contentPane);
        }
    }

    // ── Nav bar ───────────────────────────────────────────────────────────────

    private HBox buildNavBar() {
        Button backBtn = navButton("←");
        backBtn.setOnAction(e -> goBack());

        Button refreshBtn = navButton("↻");
        refreshBtn.setOnAction(e -> webEngine.reload());

        Button goBtn = navButton("Go");
        goBtn.setOnAction(e -> loadUrl(urlField.getText().trim()));
        urlField.setOnAction(e -> loadUrl(urlField.getText().trim()));

        HBox.setHgrow(urlField, Priority.ALWAYS);

        HBox bar = new HBox(8, backBtn, refreshBtn, urlField, goBtn);
        bar.setPadding(new Insets(10, 12, 10, 12));
        bar.setStyle("-fx-background-color: #1a1a2e;");
        return bar;
    }

    private Button navButton(String label) {
        Button btn = new Button(label);
        btn.setPrefHeight(36);
        btn.setMinWidth(36);
        btn.setStyle(
                "-fx-background-color: #2c2c3c;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 8;" +
                "-fx-font-size: 14px;" +
                "-fx-cursor: hand;");
        return btn;
    }
}
