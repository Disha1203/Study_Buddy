package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.controller.BrowserController;
import com.ooad.study_buddy.focus.ui.BlockPageView;
import com.ooad.study_buddy.focus.ui.TimerOverlay;
import com.ooad.study_buddy.model.RelevanceResult;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * VIEW — AI-Relevance Browser
 *
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIXES (vs previous version)
 * ═══════════════════════════════════════════════════════════════
 *
 *  BUG 1 — Blocking loop / crash from re-entrant cancel()
 *  ────────────────────────────────────────────────────────
 *  ROOT CAUSE (VIEW side): handleRelevanceResult() called
 *  webEngine.getLoadWorker().cancel() synchronously inside a
 *  BiConsumer that was invoked from within a locationProperty
 *  ChangeListener → crash. (Main fix is in BrowserController;
 *  this class no longer calls cancel() directly.)
 *
 *  FIX: handleRelevanceResult() NO LONGER calls cancel() or any
 *  engine method. It only swaps the center pane. The engine stop
 *  is handled entirely inside BrowserController.dispatchBlock().
 *
 *  BUG 2 — goBack() shows block page for previously-blocked URLs
 *  ──────────────────────────────────────────────────────────────
 *  ROOT CAUSE: goBack() loaded the previous URL. If that URL was
 *  cached as BLOCKED in BrowserController, the locationListener
 *  immediately fired onResult(BLOCKED) → block page shown for the
 *  "back" destination before the user even asked to go there.
 *
 *  FIX: goBack() now calls browserController.evict(url) for the
 *  URL it is about to navigate to. This forces a fresh evaluation
 *  (the page was previously blocked for good reason, but the user
 *  has explicitly chosen to revisit it — they can use "Allow 2 min"
 *  if needed). The eviction means we re-evaluate but don't pre-block.
 *
 *  Alternatively: if you want the back button to RESPECT cached
 *  blocks, remove the evict() call and the block page will re-appear.
 *  The current approach is more user-friendly.
 *
 *  BUG 3 — loadUrl() triggers block on itself during bypass
 *  ──────────────────────────────────────────────────────────
 *  ROOT CAUSE: grantBypass() called loadUrl(url) which pushed the
 *  current URL onto history AGAIN and then loaded it. On the next
 *  goBack(), it appeared twice in the stack.
 *
 *  FIX: grantBypass() uses webEngine.load() directly instead of
 *  loadUrl() so it doesn't corrupt the history stack.
 *
 *  BUG 4 — URL bar shows wrong URL after block
 *  ─────────────────────────────────────────────
 *  ROOT CAUSE: When BrowserController cancelled the load, the engine
 *  location would sometimes stay as the blocked URL. The URL bar
 *  showed the blocked URL even though the block page was visible.
 *
 *  FIX: handleRelevanceResult() restores the URL bar to the previous
 *  safe URL when a block is shown.
 *
 * ═══════════════════════════════════════════════════════════════
 */
public class AiBrowserView {

    private static final Logger LOG = Logger.getLogger(AiBrowserView.class.getName());

    // ── JavaFX widgets ────────────────────────────────────────────────────────
    private final WebView   webView;
    private final WebEngine webEngine;
    private final TextField urlField;

    // ── Navigation ────────────────────────────────────────────────────────────
    private final Deque<String> history    = new ArrayDeque<>();
    private       String        currentUrl = null;
    private       String        lastSafeUrl = null; // last URL that was NOT blocked

    // ── Bypass (2-minute override for borderline blocks) ──────────────────────
    private final Set<String> bypassUrls = new HashSet<>();

    // ── Layout handles ────────────────────────────────────────────────────────
    private AnchorPane  contentPane;
    private BorderPane  rootPane;
    private TimerOverlay timerOverlay;

    // ── Wired from outside ─────────────────────────────────────────────────────
    private BrowserController browserController;
    private String             topic;

    public AiBrowserView() {
        webView   = new WebView();
        webEngine = webView.getEngine();

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
                "-fx-font-family: 'Segoe UI', sans-serif;");

        // Keep URL bar in sync with engine navigation
        webEngine.locationProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank() && !newVal.equals("about:blank")) {
                Platform.runLater(() -> urlField.setText(newVal));
            }
        });
    }

    // ── Public builder ────────────────────────────────────────────────────────

    public BorderPane getView(TimerOverlay overlay,
                              BrowserController browserController,
                              String topic) {
        this.timerOverlay      = overlay;
        this.browserController = browserController;
        this.topic             = topic;

        rootPane = new BorderPane();
        rootPane.setTop(buildNavBar());
        rootPane.setStyle("-fx-background-color: #0f0f0f;");

        contentPane = new AnchorPane();
        webView.prefWidthProperty().bind(contentPane.widthProperty());
        webView.prefHeightProperty().bind(contentPane.heightProperty());
        contentPane.getChildren().add(webView);

        if (overlay != null) {
            overlay.setMaxWidth(160);
            overlay.setPrefWidth(160);
            AnchorPane.setBottomAnchor(overlay, 16.0);
            AnchorPane.setRightAnchor(overlay, 16.0);
            contentPane.getChildren().add(overlay);
        }

        rootPane.setCenter(contentPane);

        // Wire relevance checking — BrowserController guarantees FX thread
        browserController.attach(webEngine, topic, this::handleRelevanceResult);

        return rootPane;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Loads a URL, pushing the current one onto the history stack.
     * Always restores the webview (removes any block page) before loading.
     */
    public void loadUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return;
        String url = rawUrl.startsWith("http") ? rawUrl : "https://" + rawUrl;

        if (currentUrl != null) history.push(currentUrl);
        currentUrl = url;

        // Ensure webview is in view before loading
        if (rootPane.getCenter() != contentPane) {
            rootPane.setCenter(contentPane);
        }

        webEngine.load(url);
    }

    /**
     * Goes back to the previous URL.
     *
     * FIX (Bug 2): evicts the destination from the cache before loading so the
     * locationListener doesn't immediately re-block it from the stale cache
     * entry. A fresh evaluation will run instead.
     */
    public void goBack() {
        if (!history.isEmpty()) {
            String prev = history.pop();
            currentUrl = prev;

            // FIX: evict the target URL so it gets a fresh evaluation.
            // Without this, a cached BLOCKED verdict would fire onResult(BLOCKED)
            // immediately when the engine accepts the URL, showing the block page
            // for a URL the user explicitly chose to revisit.
            browserController.evict(prev);

            if (rootPane.getCenter() != contentPane) {
                rootPane.setCenter(contentPane);
            }
            webEngine.load(prev);
        } else {
            // Nothing to go back to — just make sure webview is visible
            if (rootPane.getCenter() != contentPane) {
                rootPane.setCenter(contentPane);
            }
        }
    }

    // ── Relevance result handler ──────────────────────────────────────────────

    /**
     * Called by BrowserController whenever a verdict is ready.
     * Guaranteed to run on the FX thread.
     *
     * FIX (Bug 1): Does NOT call cancel() or any WebEngine method here.
     * Engine stopping is handled inside BrowserController.dispatchBlock()
     * via Platform.runLater(). This view just swaps the center pane.
     */
    private void handleRelevanceResult(String url, RelevanceResult result) {
        // Bypass overrides a block for 2 minutes
        if (bypassUrls.contains(url) && result.isBlocked()) {
            LOG.info("[VIEW] Bypass active for: " + url);
            if (rootPane.getCenter() != contentPane) {
                rootPane.setCenter(contentPane);
            }
            return;
        }

        if (result.isBlocked()) {
            LOG.info("[VIEW] Showing block page for: " + url);

            // FIX (Bug 4): restore URL bar to last safe URL so the user
            // doesn't see the blocked URL stuck in the address bar
            if (lastSafeUrl != null) {
                urlField.setText(lastSafeUrl);
            }

            BlockPageView blockView = new BlockPageView();
            BorderPane blockPage = blockView.getView(
                    result,
                    url,
                    /* onGoBack        */ this::goBack,
                    /* onSearchInstead */ () -> searchInstead(topic),
                    /* onBypass        */ () -> grantBypass(url)
            );
            rootPane.setCenter(blockPage);

        } else {
            // Page is allowed — track it as the last safe URL
            lastSafeUrl = url;
            LOG.info("[VIEW] Allowing: " + url + " (score=" + result.getScore() + ")");

            if (rootPane.getCenter() != contentPane) {
                rootPane.setCenter(contentPane);
            }
        }
    }

    // ── Bypass logic ──────────────────────────────────────────────────────────

    /**
     * Grants a 2-minute bypass for the given URL.
     *
     * FIX (Bug 3): Uses webEngine.load() directly instead of loadUrl() to
     * avoid pushing the URL onto the history stack twice.
     */
    private void grantBypass(String url) {
        LOG.info("[VIEW] Bypass granted for 2 minutes: " + url);
        bypassUrls.add(url);
        browserController.evict(url);

        if (rootPane.getCenter() != contentPane) {
            rootPane.setCenter(contentPane);
        }
        // Load directly (don't push onto history again)
        webEngine.load(url);

        // Auto-revoke after 2 minutes
        PauseTransition expire = new PauseTransition(Duration.minutes(2));
        expire.setOnFinished(e -> {
            LOG.info("[VIEW] Bypass expired for: " + url);
            bypassUrls.remove(url);
            browserController.evict(url);
        });
        expire.play();
    }

    // ── "Search Instead" ─────────────────────────────────────────────────────

    private void searchInstead(String studyTopic) {
        String encoded = studyTopic.replace(" ", "+");
        loadUrl("https://www.google.com/search?q=" + encoded);
    }

    // ── Nav bar ───────────────────────────────────────────────────────────────

    private HBox buildNavBar() {
        Button backBtn = navButton("←");
        backBtn.setOnAction(e -> goBack());

        Button refreshBtn = navButton("↻");
        refreshBtn.setOnAction(e -> {
            if (currentUrl != null) {
                // Evict so a fresh relevance check runs on the reloaded page
                browserController.evict(currentUrl);
                webEngine.reload();
            }
        });

        Button goBtn = navButton("Go");
        goBtn.setOnAction(e -> loadUrl(urlField.getText().trim()));
        urlField.setOnAction(e -> loadUrl(urlField.getText().trim()));

        HBox.setHgrow(urlField, Priority.ALWAYS);

        HBox bar = new HBox(8, backBtn, refreshBtn, urlField, goBtn);
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