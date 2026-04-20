package com.ooad.study_buddy.controller;

import com.ooad.study_buddy.focus.FocusStateHolder;
import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.service.BlockingService;
import com.ooad.study_buddy.service.ContentExtractionService;
import com.ooad.study_buddy.service.SessionTrackingService;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * GRASP Controller — intercepts WebEngine navigation and applies relevance rules.
 *
 * CHANGES:
 *  1. isBlockingDisabled() replaces isBreakTime() so BUFFER mode also
 *     bypasses blocking (fix for Change 1 / Change 6).
 *  2. Per-engine navigation history stack tracks the LAST ALLOWED url so
 *     "Go Back" always has a valid target even on first load (fix for
 *     Change 2 / Change 3).
 *  3. Blocked pages are NEVER pushed onto the history stack so Go Back
 *     returns to the real previous page, not the blocked one.
 */
@Component
public class BrowserController {

    private static final Logger LOG = Logger.getLogger(BrowserController.class.getName());

    // ── LRU cache: url → verdict (max 64 entries) ─────────────────────────────
    private static final int CACHE_MAX = 64;
    private final Map<String, RelevanceResult> cache =
            new LinkedHashMap<>(CACHE_MAX, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, RelevanceResult> e) {
                    return size() > CACHE_MAX;
                }
            };

    // ── Cooldown: url → timestamp of last block decision (ms) ─────────────────
    private static final long COOLDOWN_MS = 1_000L;
    private final Map<String, Long> lastBlockedAt = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 64;
        }
    };

    // ── Re-entrance guard ──────────────────────────────────────────────────────
    private volatile boolean isHandling = false;

    // ── Focus/break/buffer state ───────────────────────────────────────────────
    private FocusStateHolder focusStateHolder;

    private final ContentExtractionService extractor;
    private final RelevanceController      relevanceController;
    private final BlockingService          blockingService;
    private final SessionTrackingService   sessionTrackingService;

    private ChangeListener<String>       locationListener;
    private ChangeListener<Worker.State> stateListener;

    // ── Per-engine navigation history (CHANGE 2/3: replaces AiBrowserView's deque) ──
    // Key: WebEngine identity hash, Value: stack of allowed URLs
    private final Map<Integer, Deque<String>> engineHistories = new LinkedHashMap<>();

    public BrowserController(ContentExtractionService extractor,
                             RelevanceController relevanceController,
                             BlockingService blockingService,
                             SessionTrackingService sessionTrackingService) {
        this.extractor            = extractor;
        this.relevanceController  = relevanceController;
        this.blockingService      = blockingService;
        this.sessionTrackingService = sessionTrackingService;
    }

    // ── Focus state wiring ────────────────────────────────────────────────────

    public void setFocusStateHolder(FocusStateHolder holder) {
        this.focusStateHolder = holder;
    }

    /**
     * CHANGE 1/6: Returns true when blocking should be DISABLED.
     * Covers BREAK mode and BUFFER mode — not just break.
     */
    private boolean isBlockingDisabled() {
        return focusStateHolder != null && focusStateHolder.isBlockingDisabled();
    }

    // ── Navigation history helpers (CHANGE 2/3) ───────────────────────────────

    /** Records a successfully allowed URL into this engine's history stack. */
    public void pushHistory(WebEngine engine, String url) {
        if (url == null || url.isBlank() || url.equals("about:blank")) return;
        engineHistories
                .computeIfAbsent(System.identityHashCode(engine), k -> new ArrayDeque<>())
                .push(url);
    }

    /**
     * Pops and returns the previous allowed URL for this engine.
     * Returns null if no history is available.
     */
    public String popHistory(WebEngine engine) {
        Deque<String> stack = engineHistories.get(System.identityHashCode(engine));
        if (stack == null || stack.isEmpty()) return null;
        return stack.poll();
    }

    /** Peeks at the previous URL without removing it. */
    public String peekHistory(WebEngine engine) {
        Deque<String> stack = engineHistories.get(System.identityHashCode(engine));
        if (stack == null || stack.isEmpty()) return null;
        return stack.peek();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Attaches interception listeners to {@code engine}.
     * Safe to call multiple times — old listeners are removed first.
     *
     * @param engine   WebEngine to monitor
     * @param topic    current study topic
     * @param onResult (url, result) callback — always on FX thread
     */
    public void attach(WebEngine engine, String topic,
                       BiConsumer<String, RelevanceResult> onResult) {

        // ── Remove stale listeners ─────────────────────────────────────────
        if (locationListener != null)
            engine.locationProperty().removeListener(locationListener);
        if (stateListener != null)
            engine.getLoadWorker().stateProperty().removeListener(stateListener);
        isHandling = false;

        // ══════════════════════════════════════════════════════════════════
        // PHASE 1 — locationProperty listener (instant structural decisions)
        // ══════════════════════════════════════════════════════════════════
        locationListener = (obs, oldUrl, newUrl) -> {

            if (newUrl == null || newUrl.isBlank()
                    || newUrl.equals("about:blank")
                    || !newUrl.startsWith("http")) {
                return;
            }

            if (isHandling) {
                LOG.fine("[BROWSER] Suppressing re-entrant location event: " + newUrl);
                return;
            }

            Long lastBlocked = lastBlockedAt.get(newUrl);
            if (lastBlocked != null && (System.currentTimeMillis() - lastBlocked) < COOLDOWN_MS) {
                LOG.fine("[BROWSER] Cooldown active for: " + newUrl);
                return;
            }

            // CHANGE 1/6: check isBlockingDisabled() — covers BREAK + BUFFER
            if (isBlockingDisabled()) {
                LOG.info("[BROWSER] Blocking disabled (mode=" +
                        (focusStateHolder != null ? focusStateHolder.getMode() : "unknown")
                        + ") — allowing: " + newUrl);
                // CHANGE 2: record allowed URL in history
                pushHistory(engine, oldUrl);
                onResult.accept(newUrl,
                        RelevanceResult.allowed(1.0, "Blocking disabled — all sites allowed."));
                return;
            }

            LOG.info("[BROWSER] Location changed → " + newUrl);

            if (cache.containsKey(newUrl)) {
                RelevanceResult cached = cache.get(newUrl);
                LOG.info("[BROWSER] Cache hit for " + newUrl + " → " + cached.getVerdict());
                if (cached.isBlocked()) {
                    // CHANGE 2/3: do NOT push blocked URL to history
                    dispatchBlock(engine, newUrl, cached, onResult);
                } else {
                    pushHistory(engine, oldUrl);
                }
                return;
            }

            BlockingService.Decision quick = blockingService.quickDecision(newUrl);
            LOG.info("[BROWSER] quickDecision(" + newUrl + ") = " + quick);

            if (quick == BlockingService.Decision.ALLOW) {
                RelevanceResult r = RelevanceResult.allowed(1.0, "Allowed by platform rule.");
                cache.put(newUrl, r);
                // CHANGE 2/3: record previous URL before navigating
                pushHistory(engine, oldUrl);
                onResult.accept(newUrl, r);
                sessionTrackingService.logPlatformDecision(newUrl, "ALLOW", "platform rule");

            } else if (quick == BlockingService.Decision.BLOCK) {
                RelevanceResult r = RelevanceResult.blocked(0.0, "Blocked by platform rule.");
                cache.put(newUrl, r);
                lastBlockedAt.put(newUrl, System.currentTimeMillis());
                // CHANGE 2/3: do NOT push blocked URL — history stays at previous page
                dispatchBlock(engine, newUrl, r, onResult);
                sessionTrackingService.logPlatformDecision(newUrl, "BLOCK", "platform rule");
            }
            // CHECK_RELEVANCE → wait for SUCCEEDED
        };
        engine.locationProperty().addListener(locationListener);

        // ══════════════════════════════════════════════════════════════════
        // PHASE 2 — stateProperty SUCCEEDED (full DOM-based evaluation)
        // ══════════════════════════════════════════════════════════════════
        stateListener = (obs, oldState, newState) -> {
            if (newState != Worker.State.SUCCEEDED) return;

            String url = engine.getLocation();

            if (url == null || url.isBlank()
                    || url.equals("about:blank")
                    || !url.startsWith("http")) {
                return;
            }

            // CHANGE 1/6: same check for SUCCEEDED phase
            if (isBlockingDisabled()) {
                LOG.fine("[BROWSER] Blocking disabled — skipping relevance for: " + url);
                onResult.accept(url,
                        RelevanceResult.allowed(1.0, "Blocking disabled — all sites allowed."));
                return;
            }

            if (cache.containsKey(url)) {
                LOG.fine("[BROWSER] SUCCEEDED but result already cached for: " + url);
                return;
            }

            BlockingService.Decision quick = blockingService.quickDecision(url);
            if (quick != BlockingService.Decision.CHECK_RELEVANCE) {
                LOG.fine("[BROWSER] SUCCEEDED: quick=" + quick + " (no chain needed) for " + url);
                return;
            }

            LOG.info("[BROWSER] SUCCEEDED — running relevance chain for: " + url);

            ContentData data = extractor.extract(engine, url);
            int contentLen = (data.toCombinedText() != null) ? data.toCombinedText().length() : 0;
            LOG.info("[BROWSER] Extracted content length: " + contentLen + " chars for " + url);

            RelevanceResult result = relevanceController.evaluate(topic, data);
            LOG.info(String.format("[BROWSER] Relevance result for %s → verdict=%s score=%.3f reason='%s'",
                    url, result.getVerdict(), result.getScore(), result.getReason()));

            cache.put(url, result);
            sessionTrackingService.logEvent(url, result);

            if (result.isBlocked()) {
                lastBlockedAt.put(url, System.currentTimeMillis());
                // CHANGE 2/3: blocked pages not pushed to history
                dispatchBlock(engine, url, result, onResult);
            } else {
                onResult.accept(url, result);
            }
        };
        engine.getLoadWorker().stateProperty().addListener(stateListener);
    }

    /** Clears the result cache (call when starting a new session or on mode flip). */
    public void clearCache() {
        cache.clear();
        lastBlockedAt.clear();
        isHandling = false;
    }

    /** Evicts a single URL from the cache so the next visit is re-evaluated. */
    public void evict(String url) {
        if (url != null) {
            cache.remove(url);
            lastBlockedAt.remove(url);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void dispatchBlock(WebEngine engine, String url,
                               RelevanceResult result,
                               BiConsumer<String, RelevanceResult> onResult) {
        LOG.info("[BROWSER] BLOCKING: " + url + " (score=" + result.getScore() + ")");
        isHandling = true;

        Platform.runLater(() -> {
            try {
                pauseAllMedia(engine);
                engine.getLoadWorker().cancel();
            } catch (Exception ex) {
                LOG.warning("[BROWSER] cancel() threw: " + ex.getMessage());
            } finally {
                onResult.accept(url, result);
                isHandling = false;
            }
        });
    }

    private void pauseAllMedia(WebEngine engine) {
        try {
            engine.executeScript(
                "try {" +
                "  document.querySelectorAll('video,audio').forEach(function(m){" +
                "    try { m.pause(); m.src=''; } catch(e){}" +
                "  });" +
                "  if(window.stop) window.stop();" +
                "} catch(e){}"
            );
        } catch (Exception ex) {
            LOG.fine("[BROWSER] pauseAllMedia JS error (ignored): " + ex.getMessage());
        }
    }
}