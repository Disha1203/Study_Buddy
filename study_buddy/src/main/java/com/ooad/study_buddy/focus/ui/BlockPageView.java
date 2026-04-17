package com.ooad.study_buddy.focus.ui;

import com.ooad.study_buddy.model.RelevanceResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Random;

/**
 * VIEW — Block Page
 *
 * SRP: Only renders "you're blocked" UI.
 * Low Coupling: Receives a Runnable callback for the "Go Back" action;
 *              knows nothing about WebEngine or session logic.
 */
public class BlockPageView {

    // ── Sarcastic / meme messages ─────────────────────────────────────────────
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
            "The grind doesn't stop. Neither does this blocker."
    );

    private static final Random RNG = new Random();

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Builds the blocked-page layout.
     *
     * @param result   the relevance verdict (for score display)
     * @param url      the blocked URL
     * @param onGoBack called when the user clicks "Go Back"
     * @return a BorderPane ready to swap into the scene
     */
    public BorderPane getView(RelevanceResult result, String url, Runnable onGoBack) {

        // ── Emoji ──
        Label emoji = new Label("🚫");
        emoji.setStyle("-fx-font-size: 56px;");

        // ── Main heading ──
        Label heading = new Label("This page isn't relevant.");
        heading.setStyle(
                "-fx-font-size: 26px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        // ── Sarcastic sub-message ──
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

        // ── Blocked URL label ──
        String displayUrl = url != null && url.length() > 60
                ? url.substring(0, 60) + "…"
                : (url != null ? url : "");
        Label urlLabel = new Label(displayUrl);
        urlLabel.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: #555555;" +
                "-fx-font-family: 'Consolas', monospace;");

        // ── Go Back button ──
        Button backBtn = new Button("← Go Back");
        backBtn.setPrefWidth(200);
        backBtn.setPrefHeight(44);
        backBtn.setStyle(
                "-fx-background-color: #7C6EFA;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 10;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        backBtn.setOnMouseEntered(e -> backBtn.setStyle(backBtn.getStyle()
                .replace("#7C6EFA", "#9A8FFF")));
        backBtn.setOnMouseExited(e -> backBtn.setStyle(backBtn.getStyle()
                .replace("#9A8FFF", "#7C6EFA")));
        backBtn.setOnAction(e -> { if (onGoBack != null) onGoBack.run(); });

        // ── Card ──
        VBox card = new VBox(18,
                emoji, heading, sarcasm,
                scoreChip, urlLabel, backBtn);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(460);
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

    private static String randomRoast() {
        return ROASTS.get(RNG.nextInt(ROASTS.size()));
    }
}
