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
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * GRASP Controller: Owns the Stage and orchestrates scene transitions.
 * Uses MySQL via DatabaseSeedService to load whitelist/blacklist on startup.
 */
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
        // 1. In-memory repo
        InMemorySiteMetadataRepository inMemoryRepo =
                new InMemorySiteMetadataRepository();
    
        // 2. Load whitelist/blacklist from MySQL into in-memory repo
        DatabaseSeedService seeder = new DatabaseSeedService(inMemoryRepo);
        seeder.loadFromDatabase();
    
        // 3. Wire services
        blockingService     = new BlockingService(inMemoryRepo);
        relevanceService    = new RelevanceService();
        extractor           = new ContentExtractionService();
        chainFactory        = new RelevanceChainFactory(blockingService, relevanceService);
        relevanceController = new RelevanceController(chainFactory, blockingService);
        browserController   = new BrowserController(extractor, relevanceController, blockingService); // ← 3 args
    }

    // ── Homepage ──────────────────────────────────────────────────────────────

    private void showHomepage() {
        sessionController.stopSession();

        HomepageView homepage = new HomepageView(sessionController, this::launchSession);
        Scene scene = new Scene(homepage.getView(), 1000, 600);
        scene.setFill(Color.web("#0f0f0f"));
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy");
    }

    // ── Session launch ────────────────────────────────────────────────────────

    private void launchSession(FocusSession session) {
        TimerOverlay overlay = new TimerOverlay(
                session.getTopic(),
                session.getTotalDurationMinutes());
        overlay.setOnSessionEndCallback(() -> Platform.runLater(this::showHomepage));

        sessionController.startSession(session, overlay);

        AiBrowserView browser = new AiBrowserView();
        javafx.scene.layout.BorderPane view = browser.getView(
                overlay,
                browserController,
                session.getTopic());

        Scene scene = new Scene(view, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy — " + session.getTopic());

        browser.loadUrl("https://www.google.com");
    }

    public static void main(String[] args) {
        launch();
    }
}