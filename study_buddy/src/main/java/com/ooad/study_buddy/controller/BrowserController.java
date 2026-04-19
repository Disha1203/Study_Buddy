package com.ooad.study_buddy.controller;

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

/**
 * GRASP Controller: Owns the navigation-interception lifecycle for one WebView.
 *
 * FIX — data: URI suppression
 * ─────────────────────────────
 * Pages like medicalnewstoday.com embed media via data: URIs. WebKit fires
 * locationProperty changes for these, which triggered "Unsupported protocol"
 * warnings and spurious relevance evaluations. Both listeners now guard
 * against any URL that doesn't start with "http".
 *
 * All other logic (RTTexture NPE fix, blocking loop fix, BORDERLINE fix,
 * YouTube audio fix, double quickDecision fix) preserved unchanged.
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

    // ── Public API ────────────────────────────────────────────────────────────

    public void attach(WebEngine engine, String topic,
                       BiConsumer<String, RelevanceResult> onResult) {

        if (locationListener != null)
            engine.locationProperty().removeListener(locationListener);
        if (stateListener != null)
            engine.getLoadWorker().stateProperty().removeListener(stateListener);
        isHandling = false;

        // ── PHASE 1 — locationProperty listener ───────────────────────────────
        locationListener = (obs, oldUrl, newUrl) -> {

            // Guard: ignore blank, about:blank, data: URIs, and non-http URLs.
            // data: URIs come from embedded media (e.g. medicalnewstoday images)
            // and must not be evaluated — they produce "Unsupported protocol" warnings.
            if (!isEvaluableUrl(newUrl)) return;

            if (isHandling) {
                LOG.fine("[BROWSER] Suppressing re-entrant location event: " + newUrl);
                return;
            }

            Long lastBlocked = lastBlockedAt.get(newUrl);
            if (lastBlocked != null && (System.currentTimeMillis() - lastBlocked) < COOLDOWN_MS) {
                LOG.fine("[BROWSER] Cooldown active for: " + newUrl);
                return;
            }

            LOG.info("[BROWSER] Location changed → " + newUrl);

            if (cache.containsKey(newUrl)) {
                RelevanceResult cached = cache.get(newUrl);
                LOG.info("[BROWSER] Cache hit for " + newUrl + " → " + cached.getVerdict());
                if (cached.isBlocked()) {
                    dispatchBlock(engine, newUrl, cached, onResult);
                }
                return;
            }

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

        // ── PHASE 2 — stateProperty SUCCEEDED listener ────────────────────────
        stateListener = (obs, oldState, newState) -> {
            if (newState != Worker.State.SUCCEEDED) return;

            String url = engine.getLocation();

            // Guard: ignore blank, about:blank, data: URIs, and non-http URLs.
            if (!isEvaluableUrl(url)) return;

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

            if (result.isBlocked()) {
                lastBlockedAt.put(url, System.currentTimeMillis());
                dispatchBlock(engine, url, result, onResult);
            } else {
                onResult.accept(url, result);
            }
        };
        engine.getLoadWorker().stateProperty().addListener(stateListener);
    }

    /** Clears the result cache (call when starting a new session). */
    public void clearCache() {
        cache.clear();
        lastBlockedAt.clear();
        isHandling = false;
    }

    /**
     * Evicts a single URL from the cache so the next visit is re-evaluated.
     * Called by AiBrowserView when the user clicks "Go Back" from a block page,
     * ensuring a Refresh after Go Back performs a fresh relevance check rather
     * than immediately re-showing the block page from the stale cached verdict.
     */
    public void evict(String url) {
        if (url != null) {
            cache.remove(url);
            lastBlockedAt.remove(url);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Returns true only for URLs that should be evaluated for relevance.
     *
     * Rejected:
     *   - null / blank
     *   - "about:blank"
     *   - "data:..." (embedded media — causes "Unsupported protocol" warnings)
     *   - anything not starting with "http"
     */
    private boolean isEvaluableUrl(String url) {
        if (url == null || url.isBlank()) return false;
        if (url.equals("about:blank"))    return false;
        if (url.startsWith("data:"))      return false;
        if (!url.startsWith("http"))      return false;
        return true;
    }

    private void dispatchBlock(WebEngine engine, String url,
        RelevanceResult result,
        BiConsumer<String, RelevanceResult> onResult) {
        LOG.info("[BROWSER] BLOCKING: " + url + " (score=" + result.getScore() + ")");
        isHandling = true;

        Platform.runLater(() -> {
        try {
            pauseAllMedia(engine);
            // Load about:blank instead of cancel() — avoids RTTexture teardown mid-frame
            engine.load("about:blank");
        } catch (Exception ex) {
            LOG.warning("[BROWSER] load(about:blank) threw: " + ex.getMessage());
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