package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.ui.HomepageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * GRASP - Controller: Owns the Stage and orchestrates scene transitions.
 */
public class BrowserLauncher extends Application {

    private Stage primaryStage;
    private final SessionController sessionController = new SessionController();

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        primaryStage.setTitle("Study Buddy");
        showHomepage();
        primaryStage.show();
    }

    // ── Homepage ──────────────────────────────────────────────────────────────

    private void showHomepage() {
        // Stop any running timer when returning to homepage
        sessionController.stopSession();

        HomepageView homepage = new HomepageView(sessionController, this::launchSession);
        Scene scene = new Scene(homepage.getView(), 1000, 600);
        scene.setFill(javafx.scene.paint.Color.web("#0f0f0f"));
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy");
    }

    // ── Session start → switch to browser ────────────────────────────────────

    private void launchSession(FocusSession session) {

        // Build overlay with total session duration so it can count down
        TimerOverlay overlay = new TimerOverlay(
                session.getTopic(),
                session.getTotalDurationMinutes());

        // When session ends, return to homepage on JavaFX thread
        overlay.setOnSessionEndCallback(() -> Platform.runLater(this::showHomepage));

        // Start timer — registers overlay as observer
        sessionController.startSession(session, overlay);

        // Build browser with overlay
        BrowserView browser = new BrowserView();
        Scene scene = new Scene(browser.getView(overlay), 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy — " + session.getTopic());
    }

    public static void main(String[] args) {
        launch();
    }
}