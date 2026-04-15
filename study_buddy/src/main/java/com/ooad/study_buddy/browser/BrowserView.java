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

        goButton = new Button("Go");
        goButton.setPrefHeight(35);

        setupActions();
    }

    private void setupActions() {
        goButton.setOnAction(e -> loadPage());
        urlField.setOnAction(e -> loadPage());
    }

    private void loadPage() {
        String url = urlField.getText().trim();
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }
        webEngine.load(url);
    }

    public BorderPane getView(TimerOverlay overlay) {

        HBox topBar = new HBox(10, urlField, goButton);
        topBar.setPadding(new Insets(10));

        BorderPane layout = new BorderPane();
        layout.setTop(topBar);

        if (overlay != null) {
            AnchorPane contentPane = new AnchorPane();

            webView.prefWidthProperty().bind(contentPane.widthProperty());
            webView.prefHeightProperty().bind(contentPane.heightProperty());

            AnchorPane.setBottomAnchor(overlay, 16.0);
            AnchorPane.setRightAnchor(overlay, 16.0);

            contentPane.getChildren().addAll(webView, overlay);
            layout.setCenter(contentPane);
        } else {
            layout.setCenter(webView);
        }

        return layout;
    }

    public BorderPane getView() {
        return getView(null);
    }

    // ✅ SUMMARY VIEW WITH PIE CHART
    public BorderPane getSummaryView(Runnable goHomeCallback) {

        BorderPane layout = new BorderPane();

        javafx.scene.layout.VBox box = new javafx.scene.layout.VBox(15);
        box.setStyle("-fx-alignment: center; -fx-padding: 40;");

        javafx.scene.control.Label title = new javafx.scene.control.Label("Session Summary");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        int studyTime = 50;
        int timeLost = 10;
        int distractions = 4;
        int focusScore = 83;

        javafx.scene.control.Label study = new javafx.scene.control.Label("Study Time: " + studyTime + " mins");
        javafx.scene.control.Label distractionsLbl = new javafx.scene.control.Label("Distractions: " + distractions);
        javafx.scene.control.Label lost = new javafx.scene.control.Label("Time Lost: " + timeLost + " mins");
        javafx.scene.control.Label score = new javafx.scene.control.Label("Focus Score: " + focusScore + "%");

        // 🎯 PIE CHART
        javafx.scene.chart.PieChart pieChart = new javafx.scene.chart.PieChart();
        pieChart.getData().add(new javafx.scene.chart.PieChart.Data("Study", studyTime));
        pieChart.getData().add(new javafx.scene.chart.PieChart.Data("Lost", timeLost));
        pieChart.setTitle("Time Distribution");

        javafx.scene.control.Button backBtn = new javafx.scene.control.Button("Back to Home");
        backBtn.setOnAction(e -> goHomeCallback.run());

        box.getChildren().addAll(title, pieChart, study, distractionsLbl, lost, score, backBtn);
        layout.setCenter(box);

        return layout;
    }
}