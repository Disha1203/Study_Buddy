package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.focus.ui.BlockPageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import com.ooad.study_buddy.model.RelevanceResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * VIEW — AI-Relevance Browser with multi-tab support.
 *
 * SRP  : Owns browser UI layout, tab management, and navigation bar.
 * DIP  : Accepts BrowserController; never calls services directly.
 *
 * Each tab is a self-contained TabState (WebView + history + content pane).
 * The nav bar always operates on the currently selected tab.
 *
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIX (merged from single-tab version)
 * ═══════════════════════════════════════════════════════════════
 *  BUG — BORDERLINE verdict silently allowed through
 *  ──────────────────────────────────────────────────────────────
 *  handleRelevanceResult() only checked result.isBlocked(). A BORDERLINE
 *  result (score 0.40–0.65) fell into the else-branch and was treated as
 *  ALLOWED. This is incorrect in two ways:
 *    a) Ambiguous pages should be blocked by default (stricter = safer).
 *    b) When the Python service is down, RelevanceService returns
 *       BORDERLINE(0.5) as a network fallback — meaning any page loads
 *       freely during a service outage.
 *  Fix: the block condition is now `isBlocked() || isBorderline()`.
 * ═══════════════════════════════════════════════════════════════
 */
public class AiBrowserView {

    // ── Per-tab state ─────────────────────────────────────────────────────────
    private static class TabState {
        final WebView       webView     = new WebView();
        final WebEngine     webEngine   = webView.getEngine();
        final Deque<String> history     = new ArrayDeque<>();
        String              currentUrl  = null;
        AnchorPane          contentPane = new AnchorPane();

        TabState() {
            webView.prefWidthProperty().bind(contentPane.widthProperty());
            webView.prefHeightProperty().bind(contentPane.heightProperty());
            contentPane.getChildren().add(webView);
        }
    }

    // ── Shared widgets ────────────────────────────────────────────────────────
    private final TextField urlField;
    private final TabPane   tabPane = new TabPane();

    // ── Dependencies ──────────────────────────────────────────────────────────
    private BrowserController browserController;
    private String            topic;
    private TimerOverlay      timerOverlay;

    public AiBrowserView() {
        urlField = new TextField();
        urlField.setPromptText("Search or enter URL");
        urlField.setPrefHeight(36);
        urlField.setStyle(
                "-fx-background-radius: 8;" +
                "-fx-border-radius: 8;" +
                "-fx-padding: 8;" +
                "-fx-background-color: #2c2c3c;" +
                "-fx-text-fill: white;" +
                "-fx-prompt-text-fill: #aaaaaa;" +
                // Cross-platform font fallback chain
                "-fx-font-family: 'Segoe UI', 'SF Pro Display', 'Helvetica Neue', sans-serif;");
    }

    // ── Public builder ────────────────────────────────────────────────────────

    public BorderPane getView(TimerOverlay overlay,
                              BrowserController browserController,
                              String topic) {
        this.timerOverlay      = overlay;
        this.browserController = browserController;
        this.topic             = topic;

        BorderPane root = new BorderPane();
        root.setTop(buildNavBar());
        root.setCenter(buildTabArea(overlay));
        root.setStyle("-fx-background-color: #0f0f0f;");

        // Open the first tab
        addNewTab("New Tab");

        return root;
    }

    // ── Tab area ──────────────────────────────────────────────────────────────

    private BorderPane buildTabArea(TimerOverlay overlay) {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.setStyle("-fx-background-color: #0f0f0f;");

        // Sync URL bar with active tab's current URL on tab switch
        tabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> {
                    if (newTab != null) {
                        TabState state = (TabState) newTab.getUserData();
                        String url = state.webEngine.getLocation();
                        if (url != null && !url.isBlank()) {
                            Platform.runLater(() -> urlField.setText(url));
                        }
                    }
                });

        // Overlay: floats over all tabs via a StackPane + transparent AnchorPane
        if (overlay != null) {
            overlay.setMaxWidth(160);
            overlay.setPrefWidth(160);

            AnchorPane overlayLayer = new AnchorPane();
            overlayLayer.setMouseTransparent(true); // clicks pass through to tabs
            overlayLayer.setPickOnBounds(false);
            AnchorPane.setBottomAnchor(overlay, 16.0);
            AnchorPane.setRightAnchor(overlay, 16.0);
            overlayLayer.getChildren().add(overlay);

            StackPane stack = new StackPane(tabPane, overlayLayer);
            BorderPane wrapper = new BorderPane();
            wrapper.setCenter(stack);
            return wrapper;
        }

        BorderPane wrapper = new BorderPane();
        wrapper.setCenter(tabPane);
        return wrapper;
    }

    // ── Add tab ───────────────────────────────────────────────────────────────

    private void addNewTab(String title) {
        TabState state = new TabState();

        Tab tab = new Tab(title, state.contentPane);
        tab.setUserData(state);
        tab.setStyle("-fx-background-color: #1a1a2e;");

        // Sync URL bar when this tab navigates
        state.webEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                    Platform.runLater(() -> urlField.setText(newVal));
                }
            }
        });

        // Update tab label from page title
        state.webEngine.titleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                String label = newVal.length() > 20 ? newVal.substring(0, 20) + "…" : newVal;
                Platform.runLater(() -> tab.setText(label));
            }
        });

        // Wire relevance checking for this tab
        browserController.attach(state.webEngine, topic, (url, result) ->
                Platform.runLater(() -> handleRelevanceResult(url, result, state, tab)));

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    // ── Navigation (operates on active tab) ──────────────────────────────────

    public void loadUrl(String url) {
        TabState state = activeState();
        if (state == null) return;
        if (state.currentUrl != null) state.history.push(state.currentUrl);
        state.currentUrl = url;
        String full = url.startsWith("http") ? url : "https://" + url;
        state.webEngine.load(full);
    }

    public void goBack() {
        TabState state = activeState();
        if (state == null || state.history.isEmpty()) return;
        state.currentUrl = state.history.pop();
        state.webEngine.load(state.currentUrl);
    }

    private TabState activeState() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return null;
        return (TabState) selected.getUserData();
    }

    // ── Relevance result handler ──────────────────────────────────────────────

    private void handleRelevanceResult(String url, RelevanceResult result,
                                       TabState state, Tab tab) {
        // BUG FIX: also block BORDERLINE — don't silently allow uncertain pages.
        // When the Python service is down it returns BORDERLINE(0.5) as fallback,
        // which previously allowed every page through during a service outage.
        if (result.isBlocked() || result.isBorderline()) {
            BlockPageView blockPageView = new BlockPageView();
            BorderPane blockPage = blockPageView.getView(result, url, () -> {
                tab.setContent(state.contentPane);
                goBack();
            });
            tab.setContent(blockPage);
        } else {
            // Ensure the WebView content pane is showing (restores after a block)
            tab.setContent(state.contentPane);
        }
    }

    // ── Nav bar ───────────────────────────────────────────────────────────────

    private HBox buildNavBar() {
        Button backBtn = navButton("←");
        backBtn.setOnAction(e -> goBack());

        Button refreshBtn = navButton("↻");
        refreshBtn.setOnAction(e -> {
            TabState s = activeState();
            if (s != null) s.webEngine.reload();
        });

        Button goBtn = navButton("Go");
        goBtn.setOnAction(e -> loadUrl(urlField.getText().trim()));
        urlField.setOnAction(e -> loadUrl(urlField.getText().trim()));

        // "+" opens a new tab and loads Google as the default start page
        Button newTabBtn = navButton("+");
        newTabBtn.setStyle(newTabBtn.getStyle() +
                "-fx-text-fill: #7C6EFA; -fx-font-weight: bold;");
        newTabBtn.setOnAction(e -> {
            addNewTab("New Tab");
            loadUrl("https://www.google.com");
        });

        HBox.setHgrow(urlField, Priority.ALWAYS);

        HBox bar = new HBox(8, backBtn, refreshBtn, urlField, goBtn, newTabBtn);
        bar.setPadding(new Insets(10, 12, 10, 12));
        bar.setStyle("-fx-background-color: #1a1a2e;");
        return bar;
    }

    private Button navButton(String label) {
        Button btn = new Button(label);
        btn.setPrefHeight(36);
        btn.setMinWidth(36);
        btn.setStyle(
                "-fx-background-color: #2c2c3c;" +
                "-fx-text-fill: white;" +
                "-fx-background-radius: 8;" +
                "-fx-font-size: 14px;" +
                "-fx-cursor: hand;");
        return btn;
    }
}