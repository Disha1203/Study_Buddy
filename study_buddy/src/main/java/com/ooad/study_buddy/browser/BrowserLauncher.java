package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.controller.RelevanceController;
import com.ooad.study_buddy.model.LocalSavedLinksStore;   // MERGE ADDITION
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import main.java.com.ooad.study_buddy.focus.FocusStateHolder;

import java.util.List;

/**
 * GRASP Controller — owns the Stage and orchestrates scene transitions.
 *
 * MERGE CHANGES (search-later-final → demo)
 * ──────────────────────────────────────────
 * Only ONE method is added: showSessionSummary().
 * It displays saved links (from LocalSavedLinksStore) after the session timer ends.
 *
 * The overlay's onSessionEndCallback is updated to call showSessionSummary()
 * instead of showHomepage() directly, so the user sees their saved links first.
 *
 * All existing initServices(), showHomepage(), launchSession() logic is
 * IDENTICAL to the demo branch. No services are modified.
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

    // ── Service wiring (UNCHANGED from demo) ─────────────────────────────────

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

    // ── Homepage (UNCHANGED from demo) ────────────────────────────────────────

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

    // ── Session launch (UNCHANGED from demo except onSessionEndCallback) ──────

    private void launchSession(FocusSession session) {

        sessionTrackingService.openSession(
                session.getTopic(),
                session.getStrategy().getLabel(),
                session.getTotalDurationMinutes());

        TimerOverlay overlay = new TimerOverlay(
                session.getTopic(),
                session.getTotalDurationMinutes());

        // MERGE CHANGE: route through showSessionSummary instead of showHomepage
        // so the user sees their "Save for Later" links before going back.
        overlay.setOnSessionEndCallback(() ->
                Platform.runLater(() -> {
                    sessionTrackingService.closeSession();
                    showSessionSummary();            // ← changed from showHomepage()
                }));

        sessionController.startSession(session, overlay);
        // ── 2. Create shared focus/break state flag
        FocusStateHolder focusStateHolder = new FocusStateHolder();

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

    // ── MERGE ADDITION: Session Summary ──────────────────────────────────────
    // Reuses search-later-final's LocalSavedLinksStore + summary screen pattern.
    // Styled to match the demo's dark theme; no existing classes modified.

    private void showSessionSummary() {
        List<String> saved = LocalSavedLinksStore.getInstance().getLinks();

        VBox layout = new VBox(16);
        layout.setAlignment(Pos.TOP_CENTER);
        layout.setPadding(new Insets(48, 40, 48, 40));
        layout.setStyle("-fx-background-color: #0f0f0f;");
        layout.setMaxWidth(640);

        // ── Header ──
        Label emoji = new Label("✅");
        emoji.setStyle("-fx-font-size: 48px;");

        Label title = new Label("Session Complete!");
        title.setStyle(
                "-fx-font-size: 28px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        Label subtitle = new Label("Great work. Here's what you saved for later.");
        subtitle.setStyle(
                "-fx-font-size: 14px;" +
                "-fx-text-fill: #888888;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        layout.getChildren().addAll(emoji, title, subtitle);

        // ── Saved links section ──
        if (saved.isEmpty()) {
            Label none = new Label("No links saved this session.");
            none.setStyle(
                    "-fx-text-fill: #555555;" +
                    "-fx-font-size: 13px;" +
                    "-fx-font-style: italic;" +
                    "-fx-font-family: 'Segoe UI', sans-serif;");
            layout.getChildren().add(none);
        } else {
            Label linksTitle = new Label("📌  Saved for Later");
            linksTitle.setStyle(
                    "-fx-text-fill: #7C6EFA;" +
                    "-fx-font-size: 14px;" +
                    "-fx-font-weight: bold;" +
                    "-fx-font-family: 'Segoe UI', sans-serif;");
            layout.getChildren().add(linksTitle);

            for (String link : saved) {
                Hyperlink hl = new Hyperlink(link);
                hl.setStyle(
                        "-fx-text-fill: #7C6EFA;" +
                        "-fx-font-size: 13px;" +
                        "-fx-font-family: 'Consolas', monospace;" +
                        "-fx-underline: true;");
                hl.setOnAction(e -> {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(link));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
                layout.getChildren().add(hl);
            }
        }

        // ── Go Home button ──
        Button homeBtn = new Button("Start New Session");
        homeBtn.setPrefWidth(220);
        homeBtn.setPrefHeight(46);
        homeBtn.setStyle(
                "-fx-background-color: #7C6EFA;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 12;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        homeBtn.setOnMouseEntered(e ->
                homeBtn.setStyle(homeBtn.getStyle().replace("#7C6EFA", "#9A8FFF")));
        homeBtn.setOnMouseExited(e ->
                homeBtn.setStyle(homeBtn.getStyle().replace("#9A8FFF", "#7C6EFA")));
        homeBtn.setOnAction(e -> {
            LocalSavedLinksStore.getInstance().clear(); // clear for next session
            showHomepage();
        });

        layout.getChildren().add(homeBtn);

        // ── Centre the card ──
        VBox centred = new VBox(layout);
        centred.setAlignment(Pos.CENTER);
        centred.setStyle("-fx-background-color: #0f0f0f;");

        ScrollPane scroll = new ScrollPane(centred);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: #0f0f0f; -fx-background-color: #0f0f0f;");

        primaryStage.setScene(new Scene(scroll, 1200, 680));
        primaryStage.setTitle("Study Buddy — Session Complete");
    }

    public static void main(String[] args) {
        launch();
    }
}