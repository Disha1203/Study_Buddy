package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.focus.ui.BlockPageView;
import com.ooad.study_buddy.focus.ui.OptionsView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import com.ooad.study_buddy.model.RelevanceResult;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

/**
 * VIEW — AI-Relevance Browser with multi-tab support.
 *
 * CHANGES (vs original):
 *  1. goBack() now delegates to BrowserController.popHistory() instead of
 *     a local Deque. This ensures blocked pages are never in the history
 *     stack and Go Back always works (fixes Change 2 + 3).
 *  2. TabState no longer holds its own history Deque — BrowserController
 *     owns it (single source of truth, GRASP Information Expert).
 *  3. First page load (google.com) is recorded by BrowserController
 *     automatically via pushHistory() on each allowed navigation.
 */
public class AiBrowserView {

    // ── Per-tab state ─────────────────────────────────────────────────────────
    private static class TabState {
        final WebView       webView     = new WebView();
        final WebEngine     webEngine   = webView.getEngine();
        // CHANGE 2: removed local history Deque — BrowserController owns it now
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

    private volatile boolean bufferActive = false;

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

        addNewTab("New Tab");

        return root;
    }

    // ── Tab area ──────────────────────────────────────────────────────────────

    private BorderPane buildTabArea(TimerOverlay overlay) {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.setStyle("-fx-background-color: #0f0f0f;");

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

        if (overlay != null) {
            overlay.setMaxWidth(160);
            overlay.setPrefWidth(160);

            AnchorPane overlayLayer = new AnchorPane();
            overlayLayer.setMouseTransparent(true);
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

        state.webEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                if (tabPane.getSelectionModel().getSelectedItem() == tab) {
                    Platform.runLater(() -> urlField.setText(newVal));
                }
            }
        });

        state.webEngine.titleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                String label = newVal.length() > 20 ? newVal.substring(0, 20) + "…" : newVal;
                Platform.runLater(() -> tab.setText(label));
            }
        });

        browserController.attach(state.webEngine, topic, (url, result) ->
                Platform.runLater(() -> handleRelevanceResult(url, result, state, tab)));

        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void loadUrl(String url) {
        TabState state = activeState();
        if (state == null) return;
        // CHANGE 3: track current URL before navigating (first-load fix)
        state.currentUrl = url;
        String full = url.startsWith("http") ? url : "https://" + url;
        state.webEngine.load(full);
    }

    /**
     * CHANGE 2/3: Go Back now uses BrowserController.popHistory() so it
     * always returns to the last ALLOWED page, never a blocked one.
     * Falls back to WebEngine history if BrowserController has nothing,
     * which handles the very first navigation gracefully.
     */
    public void goBack() {
        TabState state = activeState();
        if (state == null) return;

        // Try BrowserController-managed history first (most reliable)
        String prev = browserController.popHistory(state.webEngine);
        if (prev != null && !prev.isBlank() && !prev.equals("about:blank")) {
            state.currentUrl = prev;
            String full = prev.startsWith("http") ? prev : "https://" + prev;
            state.webEngine.load(full);
            return;
        }

        // Fallback: try JavaFX WebHistory
        javafx.scene.web.WebHistory history = state.webEngine.getHistory();
        int currentIndex = history.getCurrentIndex();
        if (currentIndex > 0) {
            history.go(-1);
        }
        // If neither works — silently do nothing (no crash)
    }

    private TabState activeState() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected == null) return null;
        return (TabState) selected.getUserData();
    }

    // ── Relevance result handler ──────────────────────────────────────────────

    private void handleRelevanceResult(String url, RelevanceResult result,
                                       TabState state, Tab tab) {
        if (result.isBlocked() || result.isBorderline()) {

            Runnable onGoBack = () -> {
                browserController.evict(url);
                tab.setContent(state.contentPane);
                goBack();
            };

            Runnable onOtherOptions = () ->
                    tab.setContent(buildOptionsView(url, result, state, tab, onGoBack));

            BlockPageView blockPageView = new BlockPageView();
            BorderPane blockPage = blockPageView.getView(
                    result,
                    url,
                    onGoBack,
                    onOtherOptions,
                    null
            );
            tab.setContent(blockPage);

        } else {
            // Allowed — restore web content
            tab.setContent(state.contentPane);
        }
    }

    /**
     * Builds the OptionsView for "Other Options" flow.
     */
    private BorderPane buildOptionsView(String blockedUrl,
                                        RelevanceResult result,
                                        TabState state,
                                        Tab tab,
                                        Runnable onGoBack) {
        OptionsView optionsView = new OptionsView();

        Runnable onBufferStart = () -> {
            bufferActive = true;
            browserController.clearCache();
            tab.setContent(state.contentPane);
            goBack();
        };

        Runnable onBufferEnd = () -> {
            bufferActive = false;
        };

        return optionsView.getView(result, blockedUrl, onGoBack, onBufferStart, onBufferEnd);
    }

    // ── Nav bar ────────────────────────────────────────────────────────────────

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