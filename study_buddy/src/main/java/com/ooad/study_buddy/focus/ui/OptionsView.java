package com.ooad.study_buddy.focus.ui;

import com.ooad.study_buddy.model.LocalSavedLinksStore;
import com.ooad.study_buddy.model.RelevanceResult;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * VIEW — Options Screen (shown after "Other Options" on the block page).
 *
 * Presents three choices:
 *   • Search Later  — saves the blocked URL for post-session review
 *   • 2 Min Buffer  — temporarily unlocks browsing for 120 seconds
 *   • Go Back       — returns to the previous page (same as block-page Go Back)
 *
 * Integration strategy
 * ────────────────────
 * This class is ONLY a view. It receives Runnable callbacks for every action
 * so that:
 *   - AiBrowserView (caller) owns all navigation/state decisions
 *   - LocalSavedLinksStore (from search-later-final) handles persistence
 *   - No service is modified
 *
 * SRP  : Only renders the options UI and fires callbacks.
 * DIP  : Depends on Runnable abstractions, not on concrete controllers.
 * Low Coupling : Knows nothing about WebEngine, BrowserController, or tabs.
 */
public class OptionsView {

    // Duration for the 2-minute buffer countdown (seconds)
    private static final int BUFFER_SECONDS = 120;

    /**
     * Builds the options panel as a full BorderPane so it can replace a tab's
     * content node directly (same pattern as BlockPageView).
     *
     * @param result        the RelevanceResult that caused the block (for display)
     * @param blockedUrl    the URL that was blocked
     * @param onGoBack      called when "Go Back" is clicked
     * @param onBufferStart called when the 2-min buffer STARTS (caller enables browsing)
     * @param onBufferEnd   called when the 2-min buffer EXPIRES (caller re-enables blocking)
     */
    public BorderPane getView(RelevanceResult result,
                              String blockedUrl,
                              Runnable onGoBack,
                              Runnable onBufferStart,
                              Runnable onBufferEnd) {

        // ── Header ──────────────────────────────────────────────────────────
        Label emoji = new Label("🔀");
        emoji.setStyle("-fx-font-size: 48px;");

        Label heading = new Label("What would you like to do?");
        heading.setStyle(
                "-fx-font-size: 22px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        String displayUrl = blockedUrl != null && blockedUrl.length() > 55
                ? blockedUrl.substring(0, 55) + "…"
                : (blockedUrl != null ? blockedUrl : "");
        Label urlLabel = new Label(displayUrl);
        urlLabel.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: #555555;" +
                "-fx-font-family: 'Consolas', monospace;");

        // ── Save for Later button ────────────────────────────────────────────
        Button saveLaterBtn = new Button("📌  Save for Later");
        saveLaterBtn.setPrefWidth(260);
        saveLaterBtn.setPrefHeight(46);
        saveLaterBtn.setStyle(buildBtnStyle("#7C6EFA", "white", false));
        saveLaterBtn.setOnMouseEntered(e ->
                saveLaterBtn.setStyle(buildBtnStyle("#9A8FFF", "white", false)));
        saveLaterBtn.setOnMouseExited(e ->
                saveLaterBtn.setStyle(buildBtnStyle("#7C6EFA", "white", false)));

        saveLaterBtn.setOnAction(e -> {
            // Reuse search-later-final logic: store URL in the singleton
            LocalSavedLinksStore.getInstance().addLink(blockedUrl);
            saveLaterBtn.setText("✓  Saved!");
            saveLaterBtn.setDisable(true);
            saveLaterBtn.setStyle(buildBtnStyle("#2a2a3a", "#7C6EFA", true));
            // Go back automatically so the user can continue working
            if (onGoBack != null) onGoBack.run();
        });

        // ── 2-Minute Buffer button ───────────────────────────────────────────
        Button bufferBtn = new Button("⏱  2 Min Buffer");
        bufferBtn.setPrefWidth(260);
        bufferBtn.setPrefHeight(46);
        bufferBtn.setStyle(buildBtnStyle("#2d1515", "#ff6b6b", false));
        bufferBtn.setOnMouseEntered(e ->
                bufferBtn.setStyle(buildBtnStyle("#3d1f1f", "#ff8080", false)));
        bufferBtn.setOnMouseExited(e ->
                bufferBtn.setStyle(buildBtnStyle("#2d1515", "#ff6b6b", false)));

        // Feedback label that shows the live countdown
        Label bufferStatus = new Label("");
        bufferStatus.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: #ff6b6b;" +
                "-fx-font-family: 'Consolas', monospace;");

        bufferBtn.setOnAction(e -> {
            bufferBtn.setDisable(true);
            bufferBtn.setText("⏱  2:00 remaining");
            bufferStatus.setText("Browsing unlocked — blocking resumes automatically.");

            // Notify caller to allow navigation
            if (onBufferStart != null) onBufferStart.run();

            // Visual countdown (cosmetic) + expiry callback
            int[] remaining = {BUFFER_SECONDS};
            Timeline countdown = new Timeline(
                    new KeyFrame(Duration.seconds(1), ev -> {
                        remaining[0]--;
                        int m = remaining[0] / 60;
                        int s = remaining[0] % 60;
                        bufferBtn.setText(String.format("⏱  %d:%02d remaining", m, s));
                        if (remaining[0] <= 0) {
                            bufferStatus.setText("Buffer expired — blocking is back.");
                            if (onBufferEnd != null) onBufferEnd.run();
                        }
                    }));
            countdown.setCycleCount(BUFFER_SECONDS);
            countdown.play();
        });

        // ── Go Back button ───────────────────────────────────────────────────
        Button goBackBtn = new Button("← Go Back");
        goBackBtn.setPrefWidth(260);
        goBackBtn.setPrefHeight(46);
        goBackBtn.setStyle(buildSecondaryStyle());
        goBackBtn.setOnMouseEntered(e ->
                goBackBtn.setStyle(buildSecondaryHoverStyle()));
        goBackBtn.setOnMouseExited(e ->
                goBackBtn.setStyle(buildSecondaryStyle()));
        goBackBtn.setOnAction(e -> { if (onGoBack != null) onGoBack.run(); });

        // ── Card ─────────────────────────────────────────────────────────────
        VBox actions = new VBox(10, saveLaterBtn, bufferBtn, bufferStatus, goBackBtn);
        actions.setAlignment(Pos.CENTER);

        VBox card = new VBox(20, emoji, heading, urlLabel, actions);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(380);
        card.setPadding(new Insets(48, 44, 48, 44));
        card.setStyle(
                "-fx-background-color: #161616;" +
                "-fx-background-radius: 20;" +
                "-fx-border-color: #2a2a4a;" +
                "-fx-border-radius: 20;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(124,110,250,0.12), 32, 0.2, 0, 8);");

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setCenter(wrapper);
        root.setStyle("-fx-background-color: #0f0f0f;");
        return root;
    }

    // ── Style helpers ─────────────────────────────────────────────────────────

    private String buildBtnStyle(String bg, String fg, boolean disabled) {
        return "-fx-background-color: " + bg + ";" +
               "-fx-text-fill: " + fg + ";" +
               "-fx-font-size: 13px;" +
               "-fx-font-weight: bold;" +
               "-fx-background-radius: 10;" +
               (disabled ? "" : "-fx-cursor: hand;") +
               "-fx-font-family: 'Segoe UI', sans-serif;";
    }

    private String buildSecondaryStyle() {
        return "-fx-background-color: #1e1e1e;" +
               "-fx-text-fill: #aaaaaa;" +
               "-fx-font-size: 13px;" +
               "-fx-background-radius: 10;" +
               "-fx-border-color: #333333;" +
               "-fx-border-radius: 10;" +
               "-fx-border-width: 1;" +
               "-fx-cursor: hand;" +
               "-fx-font-family: 'Segoe UI', sans-serif;";
    }

    private String buildSecondaryHoverStyle() {
        return "-fx-background-color: #2a2a2a;" +
               "-fx-text-fill: #cccccc;" +
               "-fx-font-size: 13px;" +
               "-fx-background-radius: 10;" +
               "-fx-border-color: #444444;" +
               "-fx-border-radius: 10;" +
               "-fx-border-width: 1;" +
               "-fx-cursor: hand;" +
               "-fx-font-family: 'Segoe UI', sans-serif;";
    }
}