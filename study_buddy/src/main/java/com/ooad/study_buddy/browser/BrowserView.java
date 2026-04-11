package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.focus.ui.TimerOverlay;
import javafx.geometry.Insets;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

/**
 * SRP: Only responsible for the browser UI.
 * Low Coupling: Accepts a TimerOverlay to display — does not know timer logic.
 */
public class BrowserView {

    private WebView webView;
    private WebEngine webEngine;
    private TextField urlField;
    private Button goButton;

    public BrowserView() {

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

        // URL tracking (REQUIRED FEATURE)
        webEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("Navigated to: " + newVal);
        });
    }

    private void loadPage() {
        String url = urlField.getText().trim();
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        webEngine.load(url);
    }

    /**
     * Returns the browser layout with an optional floating timer overlay
     * pinned to the bottom-right corner using AnchorPane.
     */
    public BorderPane getView(TimerOverlay overlay) {

        HBox topBar = new HBox(10, urlField, goButton);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #1e1e2f;");

        BorderPane layout = new BorderPane();
        layout.setTop(topBar);

        if (overlay != null) {
            // AnchorPane lets us pin the overlay to exact corner coordinates
            // WebView fills the entire pane; overlay sits on top, fixed size
            AnchorPane contentPane = new AnchorPane();

            webView.prefWidthProperty().bind(contentPane.widthProperty());
            webView.prefHeightProperty().bind(contentPane.heightProperty());

            overlay.setMaxWidth(160);
            overlay.setMaxHeight(Double.MAX_VALUE);
            overlay.setPrefWidth(160);

            // Pin overlay to bottom-right with 16px margin
            AnchorPane.setBottomAnchor(overlay, 16.0);
            AnchorPane.setRightAnchor(overlay, 16.0);

            contentPane.getChildren().addAll(webView, overlay);
            layout.setCenter(contentPane);
        } else {
            layout.setCenter(webView);
        }

        return layout;
    }

    /** Backward-compatible plain browser without overlay. */
    public BorderPane getView() {
        return getView(null);
    }
}