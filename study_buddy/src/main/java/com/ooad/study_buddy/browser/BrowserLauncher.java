package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.ui.HomepageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

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
            session.getTotalDurationMinutes()
    );

    // ✅ ONLY ONE INSTANCE
    BrowserView browser = new BrowserView();

    overlay.setOnSessionEndCallback(() -> Platform.runLater(() -> {

        Scene summaryScene = new Scene(
                browser.getSummaryView(this::showHomepage),
                1000, 600
        );

        primaryStage.setScene(summaryScene);
        primaryStage.setTitle("Session Summary");
    }));

    sessionController.startSession(session, overlay);

    Scene scene = new Scene(browser.getView(overlay), 1000, 600);
    primaryStage.setScene(scene);
    primaryStage.setTitle("Study Buddy — " + session.getTopic());
}

    public static void main(String[] args) {
        launch();
    }
}