package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.ui.HomepageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import com.ooad.study_buddy.distraction.ui.DistractionView;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;


/**
 * GRASP - Controller: Owns the Stage and orchestrates scene transitions.
 */
public class BrowserLauncher extends Application {

    private Scene browserScene;
    private Stage primaryStage;
    private BrowserView browser;
    private final SessionController sessionController = new SessionController();

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Study Buddy");
        showHomepage();
        primaryStage.show();
    }

    private void showHomepage() {
        sessionController.stopSession();

        HomepageView homepage = new HomepageView(sessionController, this::launchSession);
        Scene scene = new Scene(homepage.getView(), 1000, 600);
        scene.setFill(javafx.scene.paint.Color.web("#0f0f0f"));
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy");
    }

    private void launchSession(FocusSession session) {

        TimerOverlay overlay = new TimerOverlay(
                session.getTopic(),
                session.getTotalDurationMinutes());

        overlay.setOnSessionEndCallback(() -> Platform.runLater(this::showSessionSummary));

        sessionController.startSession(session, overlay);

        browser = new BrowserView(this::showDistractionView);

        // Save the scene once — reuse it every time we come back
        browserScene = new Scene(browser.getView(overlay), 1000, 600);

        primaryStage.setScene(browserScene);
        primaryStage.setTitle("Study Buddy — " + session.getTopic());
    }

    private void showDistractionView(String url) {
        DistractionView view = new DistractionView(url, () -> {
            Platform.runLater(() -> {
                browser.reset(); // clear the distracting site
                primaryStage.setScene(browserScene); // reuse existing scene — timer stays intact
                primaryStage.setTitle("Study Buddy");
            });
        });

        Scene scene = new Scene(view.getView(), 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Distraction Detected");
    }

    private void showSessionSummary() {
        java.util.List<String> saved =
            com.ooad.study_buddy.distraction.model.LocalSavedLinksStore
                .getInstance().getLinks();

        javafx.scene.layout.VBox layout = new javafx.scene.layout.VBox(15);
        layout.setAlignment(javafx.geometry.Pos.CENTER);
        layout.setPadding(new javafx.geometry.Insets(30));
        layout.setStyle("-fx-background-color: #1e1e2f;");

        javafx.scene.control.Label title = new javafx.scene.control.Label("Session Complete!");
        title.setStyle("-fx-text-fill: #7C6EFA; -fx-font-size: 22px; -fx-font-weight: bold;");
        layout.getChildren().add(title);

        if (saved.isEmpty()) {
            javafx.scene.control.Label none =
                new javafx.scene.control.Label("No links saved this session.");
            none.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 14px;");
            layout.getChildren().add(none);
        } else {
            javafx.scene.control.Label savedTitle =
                new javafx.scene.control.Label("Links saved for later:");
            savedTitle.setStyle(
                "-fx-text-fill: #cccccc; -fx-font-size: 15px; -fx-font-weight: bold;");
            layout.getChildren().add(savedTitle);

            for (String link : saved) {
                javafx.scene.control.Hyperlink hl = new javafx.scene.control.Hyperlink(link);
                hl.setStyle("-fx-text-fill: #6C63FF; -fx-font-size: 13px;");
                hl.setOnAction(e -> {
                    // open in system browser
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(link));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                layout.getChildren().add(hl);
            }
        }

        // Go Home button
        javafx.scene.control.Button homeBtn =
            new javafx.scene.control.Button("Back to Home");
        homeBtn.setPrefWidth(200);
        homeBtn.setPrefHeight(40);
        homeBtn.setStyle(
            "-fx-background-color: #7C6EFA; -fx-text-fill: white;" +
            "-fx-font-size: 13px; -fx-background-radius: 10; -fx-font-weight: bold;");
        homeBtn.setOnAction(e -> {
            com.ooad.study_buddy.distraction.model.LocalSavedLinksStore
                .getInstance().clear(); // clear for next session
            showHomepage();
        });
        layout.getChildren().add(homeBtn);

        primaryStage.setScene(new Scene(
            new javafx.scene.control.ScrollPane(layout), 1000, 600));
        primaryStage.setTitle("Session Summary");
    }

    public static void main(String[] args) {
        launch();
    }
}