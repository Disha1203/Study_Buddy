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
import com.ooad.study_buddy.service.RelevanceService;
import com.ooad.study_buddy.service.TopicValidationService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * GRASP Controller: Owns the Stage and orchestrates scene transitions.
 *
 * KEY FIXES vs original:
 *  1. BrowserController now receives BlockingService (needed for two-phase check).
 *  2. browserController.clearCache() is called on every new session so
 *     results from a previous session never leak into the next one.
 *  3. TopicValidationService is used before FocusSession is created —
 *     invalid topics are rejected with an error in HomepageView.
 *     (HomepageView.handleStart already calls the validator; kept here for
 *      belt-and-suspenders documentation clarity only.)
 */

public class BrowserLauncher extends Application {

    private Stage primaryStage;

    // ── Service graph (manually constructed — replace with Spring DI if integrated) ──
    private final SessionController      sessionController  = new SessionController();
    private final TopicValidationService validationService  = new TopicValidationService();

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
        blockingService     = new BlockingService(new InMemorySiteMetadataRepository());
        relevanceService    = new RelevanceService();
        extractor           = new ContentExtractionService();
        chainFactory        = new RelevanceChainFactory(blockingService, relevanceService);
        relevanceController = new RelevanceController(chainFactory, blockingService);

        // FIX: pass blockingService so BrowserController can run quickDecision
        //      in both the locationListener and the stateListener.
        browserController = new BrowserController(extractor, relevanceController, blockingService);

        // Pre-seed academic whitelist
        blockingService.whitelist("scholar.google.com",   "Google Scholar");
        blockingService.whitelist("arxiv.org",             "arXiv preprints");
        blockingService.whitelist("wikipedia.org",         "Wikipedia");
        blockingService.whitelist("stackoverflow.com",     "Stack Overflow");
        blockingService.whitelist("docs.oracle.com",       "Java docs");
        blockingService.whitelist("docs.spring.io",        "Spring docs");
        blockingService.whitelist("developer.mozilla.org", "MDN Web Docs");
        blockingService.whitelist("github.com",            "GitHub");
        blockingService.whitelist("pubmed.ncbi.nlm.nih.gov", "PubMed");
        blockingService.whitelist("jstor.org",             "JSTOR");
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
        // Clear stale results from any previous session
        browserController.clearCache();

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

        // Default start page — Google (will be relevance-checked)
        browser.loadUrl("https://www.google.com");
    }

    public static void main(String[] args) {
        launch();
    }
}