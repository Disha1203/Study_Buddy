package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.controller.RelevanceController;
import com.ooad.study_buddy.focus.FocusStateHolder;
import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.ui.HomepageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import com.ooad.study_buddy.relevance.RelevanceChainFactory;
import com.ooad.study_buddy.service.BlockingService;
import com.ooad.study_buddy.service.ContentExtractionService;
import com.ooad.study_buddy.service.DatabaseSeedService;
import com.ooad.study_buddy.service.RelevanceService;
import com.ooad.study_buddy.service.SessionTrackingService;
import com.ooad.study_buddy.service.TopicValidationService;

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
    private SessionTrackingService   sessionTrackingService;

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
        sessionTrackingService = new SessionTrackingService();

        browserController = new BrowserController(
                extractor,
                relevanceController,
                blockingService,
                sessionTrackingService
        );
    }

    // ── Homepage ──────────────────────────────────────────────────────────────

    private void showHomepage() {
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

        // ── 1. Open session in DB ─────────────────────────────────────────
        sessionTrackingService.openSession(
                session.getTopic(),
                session.getStrategy().getLabel(),
                session.getTotalDurationMinutes()
        );

        // ── 2. Create shared focus/break state flag ───────────────────────
        FocusStateHolder focusStateHolder = new FocusStateHolder();

        // ── 3. Wire flag into BrowserController ──────────────────────────
        browserController.setFocusStateHolder(focusStateHolder);

        // ── 4. Clear any verdicts cached from a previous session ──────────
        browserController.clearCache();

        // ── 5. Build overlay and wire BOTH references into it ────────────
        TimerOverlay overlay = new TimerOverlay(
                session.getTopic(),
                session.getTotalDurationMinutes()
        );
        overlay.setFocusStateHolder(focusStateHolder);      // so onModeChange updates the flag
        overlay.setBrowserController(browserController);    // so onModeChange clears the cache

        // ── 6. Wire session-end callback ──────────────────────────────────
        overlay.setOnSessionEndCallback(() -> Platform.runLater(() -> {
            sessionTrackingService.closeSession();
            showHomepage();
        }));

        // ── 7. Start timer (registers overlay as observer) ────────────────
        sessionController.startSession(session, overlay);

        // ── 8. Build and show browser scene ──────────────────────────────
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