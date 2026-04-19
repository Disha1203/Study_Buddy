package com.ooad.study_buddy.focus.ui;

import com.ooad.study_buddy.model.RelevanceResult;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.List;
import java.util.Random;

/**
 * VIEW — Block Page  (fixed)
 *
 * KEY FIXES:
 *  1. New overloaded getView() accepts three callbacks:
 *       onGoBack, onSearchInstead, onBypass (2-min timer shown on button)
 *  2. Original single-callback getView() preserved for backward compat.
 *  3. Bypass button shows a live countdown so the user sees the bypass expire.
 *
 * SRP: Only renders "you're blocked" UI.
 * Low Coupling: Communicates via Runnable callbacks only.
 */
public class BlockPageView {

    // ── Sarcastic messages ────────────────────────────────────────────────────
    private static final List<String> ROASTS = List.of(
            "Surely that rabbit hole can wait.",
            "Your future self thanks you for this block.",
            "Not all who wander are studying.",
            "That website won't be on the exam.",
            "Get back to work, champ.",
            "The internet will still be here after your session.",
            "Nice try. Zero points for creativity.",
            "404: Distraction not found... actually, it was found and blocked.",
            "Your study topic called. It misses you.",
            "Eyes on the prize. The prize is not this URL.",
            "Big brain energy: close the tab.",
            "Skill issue: lack of focus detected.",
            "This page has been blocked in your area.",
            "The grind doesn't stop. Neither does this blocker.",
            "Distraction.exe has been terminated.",
            "Nice URL. Would be a shame if it were blocked. Oh wait.",
            "Your attention span called. It wants to speak to the manager.",
            "Studies show this website is not your study session.",
            "This is your sign to review those flashcards."
    );

    private static final Random RNG = new Random();

    // ── Original API (backward compat) ────────────────────────────────────────

    public BorderPane getView(RelevanceResult result, String url, Runnable onGoBack) {
        return getView(result, url, onGoBack, null, null);
    }

    // ── Full API ──────────────────────────────────────────────────────────────

    /**
     * Builds the blocked-page layout.
     *
     * @param result          the relevance verdict (for score display)
     * @param url             the blocked URL
     * @param onGoBack        called when "← Go Back" is clicked
     * @param onSearchInstead called when "Search Instead" is clicked (may be null)
     * @param onBypass        called when "Allow 2 min" is clicked (may be null)
     */
    public BorderPane getView(RelevanceResult result,
                              String url,
                              Runnable onGoBack,
                              Runnable onSearchInstead,
                              Runnable onBypass) {
        // ── Emoji ──
        Label emoji = new Label("🚫");
        emoji.setStyle("-fx-font-size: 56px;");

        // ── Heading ──
        Label heading = new Label("This page isn't relevant.");
        heading.setStyle(
                "-fx-font-size: 26px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        // ── Sarcasm ──
        Label sarcasm = new Label(randomRoast());
        sarcasm.setStyle(
                "-fx-font-size: 15px;" +
                "-fx-text-fill: #aaaaaa;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        // ── Score chip ──
        String scoreText = result != null
                ? String.format("Relevance score: %.0f%%", result.getScore() * 100)
                : "Relevance score: N/A";
        Label scoreChip = new Label(scoreText);
        scoreChip.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-text-fill: #ff6b6b;" +
                "-fx-padding: 6 14;" +
                "-fx-background-radius: 20;" +
                "-fx-font-size: 12px;" +
                "-fx-font-family: 'Consolas', monospace;");

        // ── URL label ──
        String displayUrl = url != null && url.length() > 60
                ? url.substring(0, 60) + "…"
                : (url != null ? url : "");
        Label urlLabel = new Label(displayUrl);
        urlLabel.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: #555555;" +
                "-fx-font-family: 'Consolas', monospace;");

        // ── Go Back button ──
        Button backBtn = primaryButton("← Go Back");
        backBtn.setOnAction(e -> { if (onGoBack != null) onGoBack.run(); });

        // ── Action row ──
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER);
        actionRow.getChildren().add(backBtn);

        if (onSearchInstead != null) {
            Button searchBtn = secondaryButton("🔍 Search Instead");
            searchBtn.setOnAction(e -> onSearchInstead.run());
            actionRow.getChildren().add(searchBtn);
        }

        if (onBypass != null) {
            Button bypassBtn = dangerButton("⏱ Allow 2 min");
            // Countdown label updated by a PauseTransition pulse
            bypassBtn.setOnAction(e -> {
                bypassBtn.setDisable(true);
                bypassBtn.setText("⏱ 2:00");
                onBypass.run();

                // Visual countdown on the button (purely cosmetic — real timer in AiBrowserView)
                int[] remaining = {120};
                javafx.animation.Timeline countdown = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(Duration.seconds(1), ev -> {
                            remaining[0]--;
                            int m = remaining[0] / 60;
                            int s = remaining[0] % 60;
                            bypassBtn.setText(String.format("⏱ %d:%02d", m, s));
                        }));
                countdown.setCycleCount(120);
                countdown.play();
            });
            actionRow.getChildren().add(bypassBtn);
        }

        // ── Card ──
        VBox card = new VBox(18,
                emoji, heading, sarcasm,
                scoreChip, urlLabel, actionRow);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(480);
        card.setPadding(new Insets(48, 44, 48, 44));
        card.setStyle(
                "-fx-background-color: #161616;" +
                "-fx-background-radius: 20;" +
                "-fx-border-color: #3a1a1a;" +
                "-fx-border-radius: 20;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(255,80,80,0.15), 32, 0.2, 0, 8);");

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        root.setCenter(wrapper);
        root.setStyle("-fx-background-color: #0f0f0f;");
        return root;
    }

    // ── Button helpers ────────────────────────────────────────────────────────

    private Button primaryButton(String label) {
        Button btn = new Button(label);
        btn.setPrefWidth(160);
        btn.setPrefHeight(42);
        btn.setStyle(
                "-fx-background-color: #7C6EFA;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 10;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace("#7C6EFA", "#9A8FFF")));
        btn.setOnMouseExited(e  -> btn.setStyle(btn.getStyle().replace("#9A8FFF", "#7C6EFA")));
        return btn;
    }

    private Button secondaryButton(String label) {
        Button btn = new Button(label);
        btn.setPrefWidth(160);
        btn.setPrefHeight(42);
        btn.setStyle(
                "-fx-background-color: #1e1e1e;" +
                "-fx-text-fill: #aaaaaa;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #333333;" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace("#1e1e1e", "#2a2a2a")));
        btn.setOnMouseExited(e  -> btn.setStyle(btn.getStyle().replace("#2a2a2a", "#1e1e1e")));
        return btn;
    }

    private Button dangerButton(String label) {
        Button btn = new Button(label);
        btn.setPrefWidth(160);
        btn.setPrefHeight(42);
        btn.setStyle(
                "-fx-background-color: #2d1515;" +
                "-fx-text-fill: #ff6b6b;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #5a2020;" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        return btn;
    }

    private static String randomRoast() {
        return ROASTS.get(RNG.nextInt(ROASTS.size()));
    }
}