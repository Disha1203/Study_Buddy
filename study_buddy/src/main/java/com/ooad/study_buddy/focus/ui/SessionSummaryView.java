package com.ooad.study_buddy.focus.ui;

import com.ooad.study_buddy.model.LocalSavedLinksStore;
import com.ooad.study_buddy.service.SessionTrackingService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

/**
 * VIEW — Session Summary Screen (NEW CLASS)
 *
 * CHANGES (addresses Change 4 + Change 5):
 *   Change 4: Pulls real session stats from SessionTrackingService (topic,
 *             duration, strategy, timestamps) and displays them alongside
 *             the saved-links list.
 *   Change 5: Redesigned Save-for-Later UI — card layout, proper typography,
 *             scrollable link list, empty-state design, clickable hyperlinks.
 *
 * SRP  : Only responsible for rendering the post-session summary screen.
 * DIP  : Depends on SessionTrackingService for data, Runnable for navigation.
 * Low Coupling : Accepts a Runnable onHome callback so BrowserLauncher
 *               decides what happens when the user clicks "Start New Session".
 */
public class SessionSummaryView {

    private final SessionTrackingService trackingService;

    public SessionSummaryView(SessionTrackingService trackingService) {
        this.trackingService = trackingService;
    }

    /**
     * Builds the complete session summary screen.
     *
     * @param onHome  called when "Start New Session" is clicked
     * @return        a ScrollPane wrapping the full summary layout
     */
    public ScrollPane getView(Runnable onHome) {

        List<String> savedLinks = LocalSavedLinksStore.getInstance().getLinks();
        SessionTrackingService.SessionSummaryData stats = trackingService.getLastSessionSummary();

        // ── Root layout ──────────────────────────────────────────────────────
        VBox root = new VBox(0);
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #0a0a0f;");
        root.setFillWidth(true);

        // ── Header banner ────────────────────────────────────────────────────
        VBox header = buildHeader(stats);

        // ── Stats row ────────────────────────────────────────────────────────
        HBox statsRow = buildStatsRow(stats);

        // ── Main content: saved links card ───────────────────────────────────
        VBox linksCard = buildLinksCard(savedLinks);

        // ── CTA button ───────────────────────────────────────────────────────
        Button homeBtn = buildHomeButton(onHome);

        VBox content = new VBox(24, statsRow, linksCard, homeBtn);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(32, 48, 48, 48));
        content.setMaxWidth(820);
        content.setFillWidth(true);

        VBox centered = new VBox(header, content);
        centered.setAlignment(Pos.TOP_CENTER);
        centered.setFillWidth(true);
        centered.setStyle("-fx-background-color: #0a0a0f;");

        ScrollPane scroll = new ScrollPane(centered);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle(
                "-fx-background: #0a0a0f;" +
                "-fx-background-color: #0a0a0f;" +
                "-fx-border-color: transparent;");

        return scroll;
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private VBox buildHeader(SessionTrackingService.SessionSummaryData stats) {
        Label checkmark = new Label("✓");
        checkmark.setStyle(
                "-fx-font-size: 52px;" +
                "-fx-text-fill: #7C6EFA;");

        Label title = new Label("Session Complete");
        title.setStyle(
                "-fx-font-size: 34px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', 'SF Pro Display', sans-serif;");

        String topicText = (stats != null && stats.topic() != null)
                ? "You studied: " + stats.topic()
                : "Great work — session finished.";
        Label subtitle = new Label(topicText);
        subtitle.setStyle(
                "-fx-font-size: 14px;" +
                "-fx-text-fill: #666688;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        VBox header = new VBox(8, checkmark, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(48, 48, 32, 48));
        header.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #12121f, #0a0a0f);" +
                "-fx-border-color: transparent transparent #1e1e35 transparent;" +
                "-fx-border-width: 0 0 1 0;");
        return header;
    }

    // ── Stats row ─────────────────────────────────────────────────────────────

    private HBox buildStatsRow(SessionTrackingService.SessionSummaryData stats) {
        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER);

        if (stats != null) {
            row.getChildren().addAll(
                    statCard("⏱", "Duration", stats.durationMinutes() + " min"),
                    statCard("🎯", "Strategy", stats.strategyLabel()),
                    statCard("📊", "Pages Checked", String.valueOf(stats.totalEvents())),
                    statCard("🚫", "Pages Blocked", String.valueOf(stats.blockedEvents()))
            );
        } else {
            Label noData = new Label("Session stats not available.");
            noData.setStyle("-fx-text-fill: #444466; -fx-font-size: 12px;");
            row.getChildren().add(noData);
        }

        return row;
    }

    private VBox statCard(String icon, String label, String value) {
        Label iconLbl = new Label(icon);
        iconLbl.setStyle("-fx-font-size: 20px;");

        Label valueLbl = new Label(value != null ? value : "—");
        valueLbl.setStyle(
                "-fx-font-size: 20px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Consolas', monospace;");

        Label labelLbl = new Label(label);
        labelLbl.setStyle(
                "-fx-font-size: 11px;" +
                "-fx-text-fill: #555577;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        VBox card = new VBox(4, iconLbl, valueLbl, labelLbl);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16, 24, 16, 24));
        card.setMinWidth(130);
        card.setStyle(
                "-fx-background-color: #13131f;" +
                "-fx-background-radius: 14;" +
                "-fx-border-color: #1e1e35;" +
                "-fx-border-radius: 14;" +
                "-fx-border-width: 1;");
        return card;
    }

    // ── Links card ────────────────────────────────────────────────────────────

    private VBox buildLinksCard(List<String> links) {
        // Card header
        Label pinIcon = new Label("📌");
        pinIcon.setStyle("-fx-font-size: 18px;");

        Label cardTitle = new Label("Saved for Later");
        cardTitle.setStyle(
                "-fx-font-size: 16px;" +
                "-fx-font-weight: bold;" +
                "-fx-text-fill: #ffffff;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        Label cardCount = new Label(links.size() + " link" + (links.size() == 1 ? "" : "s"));
        cardCount.setStyle(
                "-fx-background-color: #7C6EFA33;" +
                "-fx-text-fill: #7C6EFA;" +
                "-fx-padding: 2 10;" +
                "-fx-background-radius: 20;" +
                "-fx-font-size: 11px;");

        HBox cardHeader = new HBox(8, pinIcon, cardTitle, cardCount);
        cardHeader.setAlignment(Pos.CENTER_LEFT);

        // Content area
        VBox linksContent;
        if (links.isEmpty()) {
            linksContent = buildEmptyState();
        } else {
            linksContent = buildLinksList(links);
        }

        VBox card = new VBox(16, cardHeader, linksContent);
        card.setPadding(new Insets(24));
        card.setMaxWidth(820);
        card.setFillWidth(true);
        card.setStyle(
                "-fx-background-color: #13131f;" +
                "-fx-background-radius: 18;" +
                "-fx-border-color: #1e1e35;" +
                "-fx-border-radius: 18;" +
                "-fx-border-width: 1;");
        return card;
    }

    private VBox buildEmptyState() {
        Label emptyIcon = new Label("🔗");
        emptyIcon.setStyle("-fx-font-size: 32px;");

        Label emptyTitle = new Label("No links saved this session");
        emptyTitle.setStyle(
                "-fx-font-size: 14px;" +
                "-fx-text-fill: #444466;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        Label emptyHint = new Label("Next time, tap 'Save for Later' on any blocked page.");
        emptyHint.setStyle(
                "-fx-font-size: 12px;" +
                "-fx-text-fill: #333355;" +
                "-fx-font-style: italic;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");

        VBox box = new VBox(8, emptyIcon, emptyTitle, emptyHint);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24, 16, 24, 16));
        return box;
    }

    private VBox buildLinksList(List<String> links) {
        VBox list = new VBox(8);
        list.setFillWidth(true);

        for (int i = 0; i < links.size(); i++) {
            String url = links.get(i);
            list.getChildren().add(buildLinkRow(i + 1, url));
        }

        // Wrap in scroll if many links
        if (links.size() > 6) {
            ScrollPane scroll = new ScrollPane(list);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(280);
            scroll.setMaxHeight(280);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setStyle(
                    "-fx-background: transparent;" +
                    "-fx-background-color: transparent;");
            VBox wrapper = new VBox(scroll);
            wrapper.setFillWidth(true);
            return wrapper;
        }

        return list;
    }

    private HBox buildLinkRow(int index, String url) {
        // Index badge
        Label indexBadge = new Label(String.valueOf(index));
        indexBadge.setMinWidth(24);
        indexBadge.setMinHeight(24);
        indexBadge.setStyle(
                "-fx-background-color: #1e1e35;" +
                "-fx-text-fill: #555577;" +
                "-fx-font-size: 11px;" +
                "-fx-background-radius: 12;" +
                "-fx-alignment: center;" +
                "-fx-font-family: 'Consolas', monospace;");

        // URL hyperlink
        Hyperlink link = new Hyperlink(url);
        link.setStyle(
                "-fx-text-fill: #8B85FF;" +
                "-fx-font-size: 12px;" +
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-underline: false;" +
                "-fx-padding: 0;");
        link.setOnMouseEntered(e -> link.setStyle(
                link.getStyle().replace("#8B85FF", "#A89FFF") + "-fx-underline: true;"));
        link.setOnMouseExited(e -> link.setStyle(
                "-fx-text-fill: #8B85FF;" +
                "-fx-font-size: 12px;" +
                "-fx-font-family: 'Consolas', monospace;" +
                "-fx-underline: false;" +
                "-fx-padding: 0;"));
        link.setOnAction(e -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ex) {
                // Silently ignore — desktop browse not available in all environments
            }
        });
        HBox.setHgrow(link, Priority.ALWAYS);

        // Copy button
        Button copyBtn = new Button("⎘");
        copyBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: #444466;" +
                "-fx-font-size: 14px;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 0 4;");
        copyBtn.setTooltip(new Tooltip("Copy URL"));
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle(
                copyBtn.getStyle().replace("#444466", "#7C6EFA")));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle(
                copyBtn.getStyle().replace("#7C6EFA", "#444466")));
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(url);
            clipboard.setContent(content);
            copyBtn.setText("✓");
            copyBtn.setStyle(copyBtn.getStyle().replace("#444466", "#3ecfcf"));
            javafx.animation.PauseTransition reset =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            reset.setOnFinished(ev -> {
                copyBtn.setText("⎘");
                copyBtn.setStyle(
                        "-fx-background-color: transparent;" +
                        "-fx-text-fill: #444466;" +
                        "-fx-font-size: 14px;" +
                        "-fx-cursor: hand;" +
                        "-fx-padding: 0 4;");
            });
            reset.play();
        });

        HBox row = new HBox(12, indexBadge, link, copyBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setFillHeight(true);
        row.setStyle(
                "-fx-background-color: #0d0d1a;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #1a1a2e;" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;");
        row.setOnMouseEntered(e -> row.setStyle(
                "-fx-background-color: #12122a;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #2a2a4a;" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;"));
        row.setOnMouseExited(e -> row.setStyle(
                "-fx-background-color: #0d0d1a;" +
                "-fx-background-radius: 10;" +
                "-fx-border-color: #1a1a2e;" +
                "-fx-border-radius: 10;" +
                "-fx-border-width: 1;"));
        return row;
    }

    // ── CTA button ────────────────────────────────────────────────────────────

    private Button buildHomeButton(Runnable onHome) {
        Button btn = new Button("Start New Session →");
        btn.setPrefWidth(240);
        btn.setPrefHeight(48);
        btn.setStyle(
                "-fx-background-color: #7C6EFA;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: bold;" +
                "-fx-background-radius: 14;" +
                "-fx-cursor: hand;" +
                "-fx-font-family: 'Segoe UI', sans-serif;");
        btn.setOnMouseEntered(e -> btn.setStyle(
                btn.getStyle().replace("#7C6EFA", "#9A8FFF")));
        btn.setOnMouseExited(e -> btn.setStyle(
                btn.getStyle().replace("#9A8FFF", "#7C6EFA")));
        btn.setOnAction(e -> {
            LocalSavedLinksStore.getInstance().clear();
            if (onHome != null) onHome.run();
        });
        return btn;
    }
}