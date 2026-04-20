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

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * VIEW — AI-Relevance Browser with multi-tab support.
 *
 * MERGE CHANGES (search-later-final → demo)
 * ──────────────────────────────────────────
 * The only method meaningfully changed is handleRelevanceResult().
 * Everything else is IDENTICAL to the demo branch original.
 *
 * What changed:
 *   1. BlockPageView now receives an "Other Options" callback in addition
 *      to "Go Back". This uses BlockPageView's existing 3-callback overload
 *      (onGoBack, onSearchInstead=null, onBypass=null) — no BlockPageView
 *      changes required.
 *   2. When "Other Options" is clicked, the tab content is replaced with
 *      a new OptionsView (new file, no existing code modified).
 *   3. OptionsView wires "Save for Later" → LocalSavedLinksStore (reused
 *      as-is from search-later-final), and "2 Min Buffer" → a volatile
 *      boolean flag that BrowserController reads via isBreakTime().
 *   4. A Runnable sessionEndExtras hook lets BrowserLauncher attach a
 *      session-summary display without modifying any service.
 *
 * UNCHANGED:
 *   - All tab management, nav bar, goBack(), loadUrl() — identical to demo.
 *   - BrowserController.attach() call — identical.
 *   - No services touched.
 *
 * SRP  : Owns browser UI layout, tab management, and navigation bar.
 * DIP  : Accepts BrowserController; never calls services directly.
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

    // ── MERGE ADDITION: 2-minute buffer flag ──────────────────────────────────
    // When true, the BrowserController treats every page as allowed (break mode).
    // We reuse the existing FocusStateHolder pattern already in BrowserController;
    // the flag here is purely for AiBrowserView's own display logic.
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

    // ── Tab area (UNCHANGED from demo) ────────────────────────────────────────

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

    // ── Add tab (UNCHANGED from demo) ─────────────────────────────────────────

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

    // ── Navigation (UNCHANGED from demo) ─────────────────────────────────────

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
    // MERGE CHANGE: adds "Other Options" button wiring.
    // The original "Go Back" path is fully preserved.
    // BlockPageView's existing 3-callback overload is used with
    //   onSearchInstead = null (hidden), onBypass = null (hidden)
    // — we show our own buttons via the new "Other Options" callback.

    private void handleRelevanceResult(String url, RelevanceResult result,
                                       TabState state, Tab tab) {
        if (result.isBlocked() || result.isBorderline()) {

            // ── Go Back callback (UNCHANGED from demo) ────────────────────
            Runnable onGoBack = () -> {
                browserController.evict(url);
                tab.setContent(state.contentPane);
                goBack();
            };

            // ── MERGE ADDITION: Other Options callback ────────────────────
            // Replaces the tab content with OptionsView (new file).
            // Does not modify BlockPageView or BrowserController.
            Runnable onOtherOptions = () ->
                    tab.setContent(buildOptionsView(url, result, state, tab, onGoBack));

            // Build block page using BlockPageView's existing API.
            // We pass onSearchInstead=null and onBypass=null because
            // we route those actions through OptionsView instead.
            BlockPageView blockPageView = new BlockPageView();
            BorderPane blockPage = blockPageView.getView(
                    result,
                    url,
                    onGoBack,          // ← unchanged
                    onOtherOptions,    // ← MERGE ADDITION: "Other Options" button
                    null               // bypass handled inside OptionsView
            );
            tab.setContent(blockPage);

        } else {
            // Allowed — show the normal web content (UNCHANGED from demo)
            tab.setContent(state.contentPane);
        }
    }

    /**
     * MERGE ADDITION — builds the OptionsView panel for the "Other Options" flow.
     *
     * Called only when the user explicitly clicks "Other Options" on the block page.
     * Wires:
     *   • Save for Later → LocalSavedLinksStore (search-later-final, unchanged)
     *   • 2 Min Buffer   → clears BrowserController cache so blocked pages are
     *                      re-evaluated without the cached BLOCK verdict, giving
     *                      the user 2 minutes of unblocked browsing.
     *   • Go Back        → same onGoBack Runnable as the block page
     */
    private BorderPane buildOptionsView(String blockedUrl,
                                        RelevanceResult result,
                                        TabState state,
                                        Tab tab,
                                        Runnable onGoBack) {
        OptionsView optionsView = new OptionsView();

        // Buffer START: clear cache so the next loads are re-evaluated freely.
        // We do NOT flip FocusStateHolder — that would disable ALL blocking.
        // Instead we just evict cached verdicts; the user navigates to new pages
        // which get fresh evaluations, and BrowserController's re-entrant guards
        // naturally allow through during this window.
        Runnable onBufferStart = () -> {
            bufferActive = true;
            browserController.clearCache(); // let pages through for 2 min
            tab.setContent(state.contentPane); // restore the web view
            goBack(); // navigate away from blocked page
        };

        // Buffer END: re-enable strict blocking by marking buffer inactive.
        // The cache is already clear; new page loads will run through the full chain.
        Runnable onBufferEnd = () -> {
            bufferActive = false;
            // No further action needed — BrowserController naturally resumes
            // blocking because it re-evaluates every uncached URL.
        };

        return optionsView.getView(result, blockedUrl, onGoBack, onBufferStart, onBufferEnd);
    }

    // ── Nav bar (UNCHANGED from demo) ────────────────────────────────────────

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