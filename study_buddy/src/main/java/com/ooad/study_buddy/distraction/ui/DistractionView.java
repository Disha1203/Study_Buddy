package com.ooad.study_buddy.distraction.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DistractionView {

    private final String blockedUrl;
    private final Runnable onGoBack;

    public DistractionView(String blockedUrl, Runnable onGoBack) {
        this.blockedUrl = blockedUrl;
        this.onGoBack = onGoBack;
    }

    public VBox getView() {

        // ── Title ──
        Label title = new Label("⚠ Distraction Detected");
        title.setStyle(
                "-fx-text-fill: #ff6b6b;" +
                "-fx-font-size: 20px;" +
                "-fx-font-weight: bold;"
        );

        // ── URL display ──
        Label urlLabel = new Label(blockedUrl);
        urlLabel.setStyle(
                "-fx-text-fill: #cccccc;" +
                "-fx-font-size: 13px;"
        );
        urlLabel.setWrapText(true);

        // ── Buttons ──
        Button saveLaterBtn = new Button("Save for Later");
        Button overrideBtn  = new Button("2-Minute Override");
        Button goBackBtn    = new Button("Go Back to Studying");

        stylePrimaryButton(saveLaterBtn);
        styleSecondaryButton(overrideBtn);
        styleGoBackButton(goBackBtn);

        // ── Actions ──
        saveLaterBtn.setOnAction(e -> {
            com.ooad.study_buddy.distraction.model.LocalSavedLinksStore
                    .getInstance()
                    .addLink(blockedUrl);

            saveLaterBtn.setText("Saved ✓");
            saveLaterBtn.setDisable(true);
            System.out.println("Saved locally: " + blockedUrl);

            // Go back to browser after saving
            if (onGoBack != null) onGoBack.run();
        });

        overrideBtn.setOnAction(e -> {
            System.out.println("2-minute override triggered");
            // Timer logic goes here in next feature
            if (onGoBack != null) onGoBack.run();
        });

        goBackBtn.setOnAction(e -> {
            if (onGoBack != null) onGoBack.run();
        });

        // ── Layout ──
        VBox layout = new VBox(15, title, urlLabel, saveLaterBtn, overrideBtn, goBackBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle("-fx-background-color: #1e1e2f;");

        return layout;
    }

    private void stylePrimaryButton(Button btn) {
        btn.setPrefWidth(200);
        btn.setPrefHeight(40);
        btn.setStyle(
                "-fx-background-color: #7C6EFA;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 10;" +
                "-fx-font-weight: bold;"
        );
    }

    private void styleSecondaryButton(Button btn) {
        btn.setPrefWidth(200);
        btn.setPrefHeight(40);
        btn.setStyle(
                "-fx-background-color: #2a2a2a;" +
                "-fx-text-fill: #cccccc;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #444444;" +
                "-fx-border-radius: 10;"
        );
    }

    private void styleGoBackButton(Button btn) {
        btn.setPrefWidth(200);
        btn.setPrefHeight(40);
        btn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #888888;" +
                "-fx-font-size: 12px;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #333333;" +
                "-fx-border-radius: 10;"
        );
    }
}