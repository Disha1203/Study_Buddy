package com.ooad.study_buddy.browser;

import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.scene.web.WebView;
import javafx.scene.web.WebEngine;

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
            "-fx-prompt-text-fill: #aaaaaa;"
        );

        goButton = new Button("Go");
        goButton.setPrefHeight(35);
        goButton.setStyle(
            "-fx-background-color: #6C63FF;" +
            "-fx-text-fill: white;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 8 15;"
        );

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

        // FIXED (no prefix error)
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        webEngine.load(url);
    }

    public BorderPane getView() {

        HBox topBar = new HBox(10, urlField, goButton);
        topBar.setPadding(new Insets(10));
        topBar.setStyle("-fx-background-color: #1e1e2f;");

        BorderPane layout = new BorderPane();
        layout.setTop(topBar);
        layout.setCenter(webView);

        return layout;
    }
}