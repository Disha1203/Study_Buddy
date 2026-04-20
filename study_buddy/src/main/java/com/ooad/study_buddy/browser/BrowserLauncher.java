package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.controller.RelevanceController;
import com.ooad.study_buddy.model.LocalSavedLinksStore;
import com.ooad.study_buddy.focus.FocusStateHolder;
import com.ooad.study_buddy.focus.controller.SessionController;
import com.ooad.study_buddy.focus.model.FocusSession;
import com.ooad.study_buddy.focus.ui.HomepageView;
import com.ooad.study_buddy.focus.ui.SessionSummaryView;
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
import javafx.scene.control.ScrollPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * GRASP Controller — owns the Stage and orchestrates scene transitions.
 *
 * CHANGES:
 *  Change 1: FocusStateHolder is now created in launchSession() and wired
 *            into BOTH TimerOverlay and BrowserController so break/buffer
 *            state correctly disables blocking.
 *  Change 4: showSessionSummary() now uses SessionSummaryView (new class)
 *            which pulls real DB stats via SessionTrackingService.
 *  Change 6: After the overlay timer ends, a 2-minute BUFFER grace period
 *            is activated via FocusStateHolder.setMode(BUFFER) before
 *            showing the summary screen.
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
    private SessionTrackingService   sessionTrackingService;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        initServices();
        primaryStage.setTitle("Study Buddy");
        showHomepage();
        primaryStage.show();
    }

    // ── Service wiring (UNCHANGED) ────────────────────────────────────────────

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
                sessionTrackingService);
    }

    // ── Homepage (UNCHANGED) ──────────────────────────────────────────────────

    private void showHomepage() {
        sessionTrackingService.closeSession();
        sessionController.stopSession();

        HomepageView homepage = new HomepageView(
                sessionController,
                this::launchSession,
                validationService,
                blockingService);

        Scene scene = new Scene(homepage.getView(), 1200, 680);
        scene.setFill(Color.web("#0f0f0f"));
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy");
    }

    // ── Session launch ────────────────────────────────────────────────────────

    private void launchSession(FocusSession session) {

        sessionTrackingService.openSession(
                session.getTopic(),
                session.getStrategy().getLabel(),
                session.getTotalDurationMinutes());

        // CHANGE 1: Create FocusStateHolder here and wire it into both
        // TimerOverlay (which flips it on mode change) and BrowserController
        // (which reads it on every navigation decision).
        FocusStateHolder focusStateHolder = new FocusStateHolder();
        browserController.setFocusStateHolder(focusStateHolder);

        TimerOverlay overlay = new TimerOverlay(
                session.getTopic(),
                session.getTotalDurationMinutes());

        // Wire the state holder into the overlay so FOCUS ↔ BREAK flips work
        overlay.setFocusStateHolder(focusStateHolder);
        overlay.setBrowserController(browserController);

        // CHANGE 6: on session end, activate BUFFER mode for 2 minutes,
        // then show the summary. This gives users a grace window.
        overlay.setOnSessionEndCallback(() -> Platform.runLater(() -> {
            sessionTrackingService.closeSession();

            // Activate BUFFER — blocking disabled, tracking disabled
            focusStateHolder.setMode(FocusStateHolder.Mode.BUFFER);
            browserController.clearCache();

            // After 2 minutes, transition to summary
            javafx.animation.PauseTransition buffer =
                    new javafx.animation.PauseTransition(
                            javafx.util.Duration.seconds(120));
            buffer.setOnFinished(e -> Platform.runLater(this::showSessionSummary));
            buffer.play();

            // Show summary immediately — buffer runs silently in background
            // so user can keep browsing for 2 min while on summary screen
            showSessionSummary();
        }));

        sessionController.startSession(session, overlay);

        AiBrowserView browser = new AiBrowserView();
        javafx.scene.layout.BorderPane view = browser.getView(
                overlay,
                browserController,
                session.getTopic());

        Scene scene = new Scene(view, 1200, 680);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy — " + session.getTopic());

        browser.loadUrl("https://www.google.com");
    }

    // ── CHANGE 4: Session Summary (now uses SessionSummaryView) ───────────────

    private void showSessionSummary() {
        // SessionSummaryView handles both saved links and DB stats
        SessionSummaryView summaryView = new SessionSummaryView(sessionTrackingService);
        ScrollPane scroll = summaryView.getView(this::showHomepage);

        primaryStage.setScene(new Scene(scroll, 1200, 680));
        primaryStage.setTitle("Study Buddy — Session Complete");
    }

    public static void main(String[] args) {
        launch();
    }
}