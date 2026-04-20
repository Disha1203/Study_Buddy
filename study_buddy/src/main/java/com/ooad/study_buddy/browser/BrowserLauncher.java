package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.controller.RelevanceController;
import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.ui.HomepageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import com.ooad.study_buddy.relevance.RelevanceChainFactory;
import com.ooad.study_buddy.service.BlockingService;
import com.ooad.study_buddy.service.ContentExtractionService;
import com.ooad.study_buddy.service.DatabaseSeedService;
import com.ooad.study_buddy.service.RelevanceService;
import com.ooad.study_buddy.service.TopicValidationService;
import com.ooad.study_buddy.service.SessionTrackingService; // ✅ ADD

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class BrowserLauncher extends Application {

    private Stage primaryStage;

    private final SessionController      sessionController = new SessionController();
    private final TopicValidationService validationService = new TopicValidationService();

    private BlockingService          blockingService;
    private RelevanceService         relevanceService;
    private ContentExtractionService extractor;
    private RelevanceChainFactory    chainFactory;
    private RelevanceController      relevanceController;
    private BrowserController        browserController;

    // ✅ ADD FIELD
    private SessionTrackingService sessionTrackingService;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        initServices();
        primaryStage.setTitle("Study Buddy");
        showHomepage();
        primaryStage.show();
    }

    // ── Service wiring ────────────────────────────────────────────────────────

    private void initServices() {
        InMemorySiteMetadataRepository inMemoryRepo =
                new InMemorySiteMetadataRepository();

        DatabaseSeedService seeder = new DatabaseSeedService(inMemoryRepo);
        seeder.loadFromDatabase();

        blockingService     = new BlockingService(inMemoryRepo);
        relevanceService    = new RelevanceService();
        extractor           = new ContentExtractionService();
        chainFactory        = new RelevanceChainFactory(blockingService, relevanceService);
        relevanceController = new RelevanceController(chainFactory, blockingService);

        // ✅ ADD: tracking service
        sessionTrackingService = new SessionTrackingService();

        // ✅ MODIFY: pass tracking service
        browserController = new BrowserController(
                extractor,
                relevanceController,
                blockingService,
                sessionTrackingService   // ✅ NEW ARG
        );
    }

    // ── Homepage ──────────────────────────────────────────────────────────────

    private void showHomepage() {
        // ✅ ADD: guard-close session
        sessionTrackingService.closeSession();

        sessionController.stopSession();

        HomepageView homepage = new HomepageView(
                sessionController,
                this::launchSession,
                validationService,
                blockingService
        );

        Scene scene = new Scene(homepage.getView(), 1200, 680);
        scene.setFill(Color.web("#0f0f0f"));
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy");
    }

    // ── Session launch ────────────────────────────────────────────────────────

    private void launchSession(FocusSession session) {

        // ✅ ADD: open session BEFORE anything
        sessionTrackingService.openSession(
                session.getTopic(),
                session.getStrategy().getLabel(),
                session.getTotalDurationMinutes()
        );

        TimerOverlay overlay = new TimerOverlay(
                session.getTopic(),
                session.getTotalDurationMinutes()
        );

        // ✅ MODIFY: close session on timer end
        overlay.setOnSessionEndCallback(() -> Platform.runLater(() -> {
            sessionTrackingService.closeSession(); // ✅ ADD
            showHomepage();
        }));

        sessionController.startSession(session, overlay);

        AiBrowserView browser = new AiBrowserView();
        javafx.scene.layout.BorderPane view = browser.getView(
                overlay,
                browserController,
                session.getTopic()
        );

        Scene scene = new Scene(view, 1200, 680);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy — " + session.getTopic());

        browser.loadUrl("https://www.google.com");
    }

    public static void main(String[] args) {
        launch();
    }
}