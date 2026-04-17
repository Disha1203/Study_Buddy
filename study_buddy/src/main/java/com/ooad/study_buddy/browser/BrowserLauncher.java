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
import javafx.stage.Stage;

/**
 * GRASP Controller: Owns the Stage and orchestrates scene transitions.
 *
 * This replaces the original BrowserLauncher with AI relevance support.
 * The HomepageView is UNCHANGED — we just wire new services in launchSession().
 *
 * NOTE: In a Spring Boot + JavaFX setup you would inject these via
 *       ApplicationContext.getBean(). Here they are constructed manually
 *       because JavaFX Application.launch() does not go through Spring DI.
 *       Wire with Spring if using SpringApplication + JavaFX integration lib.
 */
public class BrowserLauncher extends Application {

    private Stage primaryStage;

    // ── Manually constructed service graph ───────────────────────────────────
    // (Replace with @Autowired / ApplicationContext if using Spring JavaFX boot)
    private final SessionController    sessionController    = new SessionController();
    private final TopicValidationService validationService  = new TopicValidationService();

    // These would normally be Spring beans:
    private BlockingService         blockingService;
    private RelevanceService        relevanceService;
    private ContentExtractionService extractor;
    private RelevanceChainFactory   chainFactory;
    private RelevanceController     relevanceController;
    private BrowserController       browserController;

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
        // In a real Spring Boot app, replace this block with @Autowired fields
        // and remove the manual construction entirely.
        // For now, we instantiate with a no-op repository (swap for Spring bean).
        blockingService     = new BlockingService(new InMemorySiteMetadataRepository());
        relevanceService    = new RelevanceService();
        extractor           = new ContentExtractionService();
        chainFactory        = new RelevanceChainFactory(blockingService, relevanceService);
        relevanceController = new RelevanceController(chainFactory, blockingService);
        browserController   = new BrowserController(extractor, relevanceController);

        // Pre-seed some whitelisted academic domains
        blockingService.whitelist("scholar.google.com",   "Google Scholar");
        blockingService.whitelist("arxiv.org",             "arXiv preprints");
        blockingService.whitelist("wikipedia.org",         "Wikipedia");
        blockingService.whitelist("stackoverflow.com",     "Stack Overflow");
        blockingService.whitelist("docs.oracle.com",       "Java docs");
        blockingService.whitelist("docs.spring.io",        "Spring docs");
        blockingService.whitelist("developer.mozilla.org", "MDN Web Docs");
    }

    // ── Homepage ──────────────────────────────────────────────────────────────

    private void showHomepage() {
        sessionController.stopSession();

        HomepageView homepage = new HomepageView(sessionController, this::launchSession);
        javafx.scene.Scene scene = new javafx.scene.Scene(homepage.getView(), 1000, 600);
        scene.setFill(javafx.scene.paint.Color.web("#0f0f0f"));
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

        // Build the AI-enabled browser view
        AiBrowserView browser = new AiBrowserView();
        javafx.scene.layout.BorderPane view = browser.getView(
                overlay,
                browserController,
                session.getTopic());

        Scene scene = new Scene(view, 1000, 600);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Study Buddy — " + session.getTopic());

        // Load a sensible default start page
        browser.loadUrl("https://www.google.com");
    }

    public static void main(String[] args) {
        launch();
    }
}
