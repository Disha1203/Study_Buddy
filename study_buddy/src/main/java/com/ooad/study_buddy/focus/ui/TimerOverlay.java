package com.ooad.study_buddy.focus.ui;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.focus.FocusStateHolder;
import com.ooad.study_buddy.focus.observer.TimerObserver;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Observer Pattern - Concrete Observer: Reacts to timer events and updates UI.
 */
public class TimerOverlay extends VBox implements TimerObserver {

    private final Label modeLabel;
    private final Label timerLabel;
    private final Label topicLabel;
    private final Label sessionLabel;

    private int sessionRemainingSeconds;

    private static final String FOCUS_ACCENT = "#7C6EFA";
    private static final String BREAK_ACCENT = "#3ecfcf";
    private static final String DONE_ACCENT  = "#f0a500";

    private Runnable onSessionEndCallback;

    // ── Fields for break-mode blocking ───────────────────────────────────────
    private FocusStateHolder  focusStateHolder;
    private BrowserController browserController;

    public TimerOverlay(String topic, int totalSessionMinutes) {

        this.sessionRemainingSeconds = totalSessionMinutes * 60;

        modeLabel    = new Label("FOCUS");
        timerLabel   = new Label("--:--");
        topicLabel   = new Label(topic.length() > 18
                ? topic.substring(0, 18) + "…" : topic);
        sessionLabel = new Label("Session: " + formatTime(sessionRemainingSeconds));

        styleLabels();

        setAlignment(Pos.CENTER);
        setPadding(new Insets(12, 18, 14, 18));
        setSpacing(2);
        setMaxWidth(170);
        setStyle(buildCardStyle(FOCUS_ACCENT));

        getChildren().addAll(topicLabel, modeLabel, timerLabel, sessionLabel);
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setOnSessionEndCallback(Runnable callback) {
        this.onSessionEndCallback = callback;
    }

    public void setFocusStateHolder(FocusStateHolder holder) {
        this.focusStateHolder = holder;
    }

    public void setBrowserController(BrowserController controller) {
        this.browserController = controller;
    }

    // ── TimerObserver ─────────────────────────────────────────────────────────

    @Override
    public void onTick(int remainingSeconds) {
        sessionRemainingSeconds = Math.max(0, sessionRemainingSeconds - 1);
        Platform.runLater(() -> {
            timerLabel.setText(formatTime(remainingSeconds));
            sessionLabel.setText("Session: " + formatTime(sessionRemainingSeconds));
        });
    }

    @Override
    public void onModeChange(boolean isFocus) {
        // ── 1. Update the shared flag so BrowserController sees the change ──
        if (focusStateHolder != null) {
            focusStateHolder.setFocusMode(isFocus);
        }

        // ── 2. Clear cached verdicts so sites blocked during focus are
        //       immediately accessible on break, and vice versa ────────────
        if (browserController != null) {
            browserController.clearCache();
        }

        // ── 3. Update the UI ──────────────────────────────────────────────
        Platform.runLater(() -> {
            String accent = isFocus ? FOCUS_ACCENT : BREAK_ACCENT;
            modeLabel.setText(isFocus ? "FOCUS" : "BREAK");
            applyAccent(accent);
        });
    }

    @Override
    public void onSessionEnd() {
        // ── 1. Mark as not-focus so no blocking lingers after session ends ─
        if (focusStateHolder != null) {
            focusStateHolder.setFocusMode(false);
        }

        // ── 2. Clear cache so nothing stale carries over ──────────────────
        if (browserController != null) {
            browserController.clearCache();
        }

        // ── 3. Update UI then fire the session-end callback ───────────────
        Platform.runLater(() -> {
            modeLabel.setText("DONE ✓");
            timerLabel.setText("00:00");
            sessionLabel.setText("Session: 00:00");
            applyAccent(DONE_ACCENT);

            javafx.animation.PauseTransition pause =
                    new javafx.animation.PauseTransition(
                            javafx.util.Duration.seconds(2));
            pause.setOnFinished(e -> {
                if (onSessionEndCallback != null) onSessionEndCallback.run();
            });
            pause.play();
        });
    }

    // ── Style helpers ─────────────────────────────────────────────────────────

    private void styleLabels() {
        topicLabel.setStyle(
                "-fx-font-size: 10px;" +
                "-fx-text-fill: #999999;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        modeLabel.setStyle(
                "-fx-font-size: 10px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + FOCUS_ACCENT + ";" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        timerLabel.setStyle(
                "-fx-font-size: 26px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: white;" +
                "-fx-font-family: 'Consolas', monospace;");
        sessionLabel.setStyle(
                "-fx-font-size: 10px;" +
                "-fx-text-fill: #777777;" +
                "-fx-font-family: 'Segoe UI', sans-serif;" +
                "-fx-padding: 3 0 0 0;");
    }

    private void applyAccent(String color) {
        modeLabel.setStyle(
                "-fx-font-size: 10px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: " + color + ";" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        setStyle(buildCardStyle(color));
    }

    private String buildCardStyle(String color) {
        return "-fx-background-color: rgba(15,15,15,0.88);" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: " + hexToRgba(color, 0.5) + ";" +
                "-fx-border-radius: 14;" +
                "-fx-border-width: 1.2;" +
                "-fx-effect: dropshadow(gaussian, " +
                hexToRgba(color, 0.4) + ", 14, 0.3, 0, 3);";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private String hexToRgba(String hex, double alpha) {
        hex = hex.replace("#", "");
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);
        return String.format("rgba(%d,%d,%d,%.2f)", r, g, b, alpha);
    }

    private String formatTime(int totalSeconds) {
        int m = totalSeconds / 60;
        int s = totalSeconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}