package com.ooad.study_buddy.controller;

import com.ooad.study_buddy.focus.FocusStateHolder;
import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.service.BlockingService;
import com.ooad.study_buddy.service.ContentExtractionService;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

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

    // ── Focus/break state ──────────────────────────────────────────────────────
    private FocusStateHolder focusStateHolder;

    private final ContentExtractionService extractor;
    private final RelevanceController      relevanceController;
    private final BlockingService          blockingService;

    private ChangeListener<String>       locationListener;
    private ChangeListener<Worker.State> stateListener;

    public BrowserController(ContentExtractionService extractor,
                             RelevanceController relevanceController,
                             BlockingService blockingService) {
        this.extractor           = extractor;
        this.relevanceController = relevanceController;
        this.blockingService     = blockingService;
    }

    // ── Focus state wiring ────────────────────────────────────────────────────

    public void setFocusStateHolder(FocusStateHolder holder) {
        this.focusStateHolder = holder;
    }

    /**
     * Returns true when it is currently break time, meaning all blocking
     * should be skipped and every URL should be allowed through freely.
     */
    private boolean isBreakTime() {
        return focusStateHolder != null && !focusStateHolder.isFocusMode();
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
        // PHASE 1 — locationProperty listener
        //   Fires as soon as WebEngine accepts a new URL (BEFORE network).
        //   Used for INSTANT decisions: whitelist / blacklist / platform rules.
        //   CHECK_RELEVANCE URLs are NOT decided here — they wait for the DOM.
        //
        //   CRITICAL: Never call engine.load(), engine.stop(), or
        //   getLoadWorker().cancel() directly in this callback.
        //   Always defer engine mutations via Platform.runLater().
        // ══════════════════════════════════════════════════════════════════
        locationListener = (obs, oldUrl, newUrl) -> {

            // Guard 1: ignore empty / about:blank / non-http URLs
            if (newUrl == null || newUrl.isBlank()
                    || newUrl.equals("about:blank")
                    || !newUrl.startsWith("http")) {
                return;
            }

            // Guard 2: suppress URLs triggered by our own cancel() / load()
            if (isHandling) {
                LOG.fine("[BROWSER] Suppressing re-entrant location event: " + newUrl);
                return;
            }

            // Guard 3: same URL blocked less than COOLDOWN_MS ago → skip
            Long lastBlocked = lastBlockedAt.get(newUrl);
            if (lastBlocked != null && (System.currentTimeMillis() - lastBlocked) < COOLDOWN_MS) {
                LOG.fine("[BROWSER] Cooldown active for: " + newUrl);
                return;
            }

            // ── Break time: skip all blocking ─────────────────────────────
            if (isBreakTime()) {
                LOG.info("[BROWSER] Break time — allowing (location): " + newUrl);
                onResult.accept(newUrl,
                        RelevanceResult.allowed(1.0, "Break time — all sites allowed."));
                return;
            }

            LOG.info("[BROWSER] Location changed → " + newUrl);

            // Cache hit (previous decision for this URL)
            if (cache.containsKey(newUrl)) {
                RelevanceResult cached = cache.get(newUrl);
                LOG.info("[BROWSER] Cache hit for " + newUrl + " → " + cached.getVerdict());
                if (cached.isBlocked()) {
                    dispatchBlock(engine, newUrl, cached, onResult);
                }
                return;
            }

            // Quick structural decision — no DOM needed
            BlockingService.Decision quick = blockingService.quickDecision(newUrl);
            LOG.info("[BROWSER] quickDecision(" + newUrl + ") = " + quick);

            if (quick == BlockingService.Decision.ALLOW) {
                RelevanceResult r = RelevanceResult.allowed(1.0, "Allowed by platform rule.");
                cache.put(newUrl, r);
                onResult.accept(newUrl, r);

            } else if (quick == BlockingService.Decision.BLOCK) {
                RelevanceResult r = RelevanceResult.blocked(0.0, "Blocked by platform rule.");
                cache.put(newUrl, r);
                lastBlockedAt.put(newUrl, System.currentTimeMillis());
                dispatchBlock(engine, newUrl, r, onResult);
            }
            // CHECK_RELEVANCE → wait for SUCCEEDED (phase 2)
        };
        engine.locationProperty().addListener(locationListener);

        // ══════════════════════════════════════════════════════════════════
        // PHASE 2 — stateProperty SUCCEEDED listener
        //   Fires once the DOM is fully loaded and JS is executable.
        //   Only runs the full relevance chain for CHECK_RELEVANCE URLs.
        // ══════════════════════════════════════════════════════════════════
        stateListener = (obs, oldState, newState) -> {
            if (newState != Worker.State.SUCCEEDED) return;

            String url = engine.getLocation();

            // Guard: ignore blank / about:blank
            if (url == null || url.isBlank()
                    || url.equals("about:blank")
                    || !url.startsWith("http")) {
                return;
            }

            // ── Break time: skip all blocking ─────────────────────────────
            if (isBreakTime()) {
                LOG.fine("[BROWSER] Break time — skipping relevance for: " + url);
                onResult.accept(url,
                        RelevanceResult.allowed(1.0, "Break time — all sites allowed."));
                return;
            }

            // Already decided in phase 1 or cached
            if (cache.containsKey(url)) {
                LOG.fine("[BROWSER] SUCCEEDED but result already cached for: " + url);
                return;
            }

            // Only run the chain for URLs that need content evaluation
            BlockingService.Decision quick = blockingService.quickDecision(url);
            if (quick != BlockingService.Decision.CHECK_RELEVANCE) {
                LOG.fine("[BROWSER] SUCCEEDED: quick=" + quick + " (no chain needed) for " + url);
                return;
            }

            LOG.info("[BROWSER] SUCCEEDED — running relevance chain for: " + url);

            // Extract page content from the loaded DOM
            ContentData data = extractor.extract(engine, url);
            int contentLen = (data.toCombinedText() != null) ? data.toCombinedText().length() : 0;
            LOG.info("[BROWSER] Extracted content length: " + contentLen + " chars for " + url);

            // Run the full Chain-of-Responsibility
            RelevanceResult result = relevanceController.evaluate(topic, data);
            LOG.info(String.format("[BROWSER] Relevance result for %s → verdict=%s score=%.3f reason='%s'",
                    url, result.getVerdict(), result.getScore(), result.getReason()));

            cache.put(url, result);

            if (result.isBlocked()) {
                lastBlockedAt.put(url, System.currentTimeMillis());
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