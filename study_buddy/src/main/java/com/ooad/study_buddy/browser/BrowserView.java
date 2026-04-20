package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.focus.ui.TimerOverlay;
import javafx.geometry.Insets;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

import java.util.function.Consumer;

/**
 * SRP: Only responsible for the browser UI.
 * Low Coupling: Accepts a TimerOverlay to display — does not know timer logic.
 */
public class BrowserView {

    private WebView webView;
    private WebEngine webEngine;
    private TextField urlField;
    private Button goButton;
    private final Consumer<String> onDistraction;

    public BrowserView(Consumer<String> onDistraction) {
        this.onDistraction = onDistraction;

        webView = new WebView();
        webEngine = webView.getEngine();

        urlField = new TextField();
        urlField.setPromptText("Search or enter URL");
        urlField.setPrefHeight(35);
        urlField.setStyle(
                "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;" +
                        "-fx-padding: 8;" +
                        "-fx-background-color: #2c2c3c;" +
                        "-fx-text-fill: white;" +
                        "-fx-prompt-text-fill: #aaaaaa;");

        goButton = new Button("Go");
        goButton.setPrefHeight(35);
        goButton.setStyle(
                "-fx-background-color: #6C63FF;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 8;" +
                        "-fx-padding: 8 15;");

        setupActions();
    }

    private void setupActions() {
        goButton.setOnAction(e -> loadPage());
        urlField.setOnAction(e -> loadPage());

        // When navigation happens, check for distracting sites
        webEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                System.out.println("Navigated to: " + newVal);
                checkDistraction(newVal);
            }
        });
    }

    private void loadPage() {
        String url = urlField.getText().trim();
        if (!url.isBlank()) {
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            webEngine.load(url);
        }
    }

    /** Clears the browser back to a neutral page */
    public void reset() {
        urlField.setText("");
        webEngine.load("about:blank");
    }

    /**
     * Placeholder distraction check.
     * Replace the keywords list with your actual logic if needed.
     */
    private void checkDistraction(String url) {
        String[] distractingSites = {
                "youtube.com", "instagram.com", "twitter.com",
                "facebook.com", "reddit.com", "netflix.com"
        };
        for (String site : distractingSites) {
            if (url.contains(site)) {
                if (onDistraction != null) {
                    onDistraction.accept(url);
                }
                return;
            }
        }
    }

    /**
     * Returns the browser layout with the timer overlay
     * pinned to the bottom-right corner using AnchorPane.
     */
    public BorderPane getView(TimerOverlay overlay) {

        HBox topBar = new HBox(10, urlField, goButton);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #1e1e2f;");

        BorderPane layout = new BorderPane();
        layout.setTop(topBar);

        if (overlay != null) {
            AnchorPane contentPane = new AnchorPane();

            webView.prefWidthProperty().bind(contentPane.widthProperty());
            webView.prefHeightProperty().bind(contentPane.heightProperty());

            overlay.setMaxWidth(160);
            overlay.setMaxHeight(Double.MAX_VALUE);
            overlay.setPrefWidth(160);

            AnchorPane.setBottomAnchor(overlay, 16.0);
            AnchorPane.setRightAnchor(overlay, 16.0);

            contentPane.getChildren().addAll(webView, overlay);
            layout.setCenter(contentPane);
        } else {
            layout.setCenter(webView);
        }

        return layout;
    }

    /** Plain browser without overlay. */
    public BorderPane getView() {
        return getView(null);
    }
}