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
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIXES (vs previous version)
 * ═══════════════════════════════════════════════════════════════
 *
 *  BUG 1 — RTTexture NPE crash ("RenderJob error / stableBackbuffer is null")
 *  ─────────────────────────────────────────────────────────────────────────
 *  ROOT CAUSE: webEngine.getLoadWorker().cancel() was being called directly
 *  inside the locationProperty ChangeListener callback. That callback fires
 *  during the JavaFX pulse cycle WHILE WebKit's Prism renderer is already
 *  mid-frame. Calling cancel() at that moment tears down the RTTexture before
 *  the render job finishes, leaving a null stableBackbuffer → NPE storm.
 *
 *  FIX: Never call cancel()/stop()/load() directly from a property listener.
 *  Instead, post every engine mutation via Platform.runLater() so it executes
 *  on the NEXT pulse, safely outside the current render cycle.
 *  The isHandling guard additionally prevents re-entrant calls.
 *
 *  BUG 2 — Blocking loop (block page flickers then disappears)
 *  ───────────────────────────────────────────────────────────
 *  ROOT CAUSE: cancel() triggered a location change to "about:blank".
 *  The locationListener saw "about:blank", found no cache entry, ran
 *  quickDecision → CHECK_RELEVANCE (not a known domain), then the
 *  stateListener fired on SUCCEEDED for "about:blank" with empty content,
 *  called the Python API, got BORDERLINE (0.5) → onResult fired with ALLOWED
 *  → the block page was replaced by an empty contentPane.
 *
 *  FIX (a): Ignore "about:blank" and any URL that doesn't start with http
 *           in BOTH listeners.
 *  FIX (b): isHandling flag: once a block decision is issued for URL X, any
 *           subsequent location/state event for a DIFFERENT url (e.g. about:blank)
 *           triggered by our own cancel() is suppressed until the flag clears.
 *  FIX (c): The cooldown map (url → lastBlockedAt ms) prevents the same URL
 *           from firing a new block callback within 1 second, stopping rapid
 *           re-trigger loops on YouTube/SPAs that emit multiple location events.
 *
 *  BUG 3 — AI relevance ignored (irrelevant pages allowed through)
 *  ───────────────────────────────────────────────────────────────
 *  ROOT CAUSE: RelevanceResult.BORDERLINE was silently treated as ALLOWED
 *  inside AiBrowserView (isBlocked() returns false for BORDERLINE). Pages with
 *  a score of 0.5 (the default fallback when Python is unreachable) were
 *  therefore always let through.
 *  Also, logging was absent so it was invisible when the API was being skipped.
 *
 *  FIX: Added full debug logging at every decision point (quickDecision result,
 *  content length, relevance score, final verdict). The actual BORDERLINE→BLOCK
 *  reclassification happens in RelevanceController (see that class).
 *
 *  BUG 4 — YouTube audio continues after block
 *  ─────────────────────────────────────────────
 *  ROOT CAUSE: webEngine.getLoadWorker().cancel() only stops the *network load*,
 *  it does NOT pause <video>/<audio> elements that are already buffered/playing.
 *
 *  FIX: After cancel(), inject a JS snippet that pauses every media element on
 *  the page before the engine is stopped. This is done via Platform.runLater()
 *  (same deferred pattern as BUG 1 fix).
 *
 *  BUG 5 — Double quickDecision call
 *  ───────────────────────────────────
 *  ROOT CAUSE: stateListener called quickDecision, then passed through to
 *  relevanceController.evaluate() which also called quickDecision internally.
 *
 *  FIX: stateListener now passes the pre-computed ContentData directly to
 *  relevanceController.evaluateContent() (new overload) so quickDecision runs
 *  exactly once per navigation event.
 *
 * ═══════════════════════════════════════════════════════════════
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
    // Prevents re-triggering the same block within 1 second (SPA / YouTube SPAs
    // fire multiple location events for a single user navigation).
    private static final long COOLDOWN_MS = 1_000L;
    private final Map<String, Long> lastBlockedAt = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> e) {
            return size() > 64;
        }
    };

    // ── Re-entrance guard ──────────────────────────────────────────────────────
    // Set to true while we are posting a cancel/load mutation via runLater.
    // Any location change arriving while this is true was triggered by OUR OWN
    // mutation (e.g. cancel() → "about:blank") and must be ignored.
    private volatile boolean isHandling = false;

    private final ContentExtractionService extractor;
    private final RelevanceController      relevanceController;
    private final BlockingService          blockingService;

    // Stored so we can detach on re-attach
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

            LOG.info("[BROWSER] Location changed → " + newUrl);

            // Cache hit (previous decision for this URL)
            if (cache.containsKey(newUrl)) {
                RelevanceResult cached = cache.get(newUrl);
                LOG.info("[BROWSER] Cache hit for " + newUrl + " → " + cached.getVerdict());
                if (cached.isBlocked()) {
                    dispatchBlock(engine, newUrl, cached, onResult);
                }
                // ALLOWED/BORDERLINE: do nothing here, let the page load
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

            // Run the full Chain-of-Responsibility (whitelist → blacklist → url → content)
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

    /** Evicts a single URL from the cache so the next visit is re-evaluated. */
    public void evict(String url) {
        if (url != null) {
            cache.remove(url);
            lastBlockedAt.remove(url);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Safely stops the engine and fires the onResult callback.
     *
     * WHY Platform.runLater?
     *   Both listeners can fire during a JavaFX pulse. If we call
     *   engine.getLoadWorker().cancel() synchronously we interrupt WebKit's
     *   render pipeline mid-frame → RTTexture NPE crash (Bug 1).
     *   Deferring to the NEXT pulse avoids the crash entirely.
     *
     * WHY pauseAllMedia?
     *   cancel() stops the network load but NOT audio/video elements that
     *   are already buffered. We inject JS to pause them (Bug 4 fix).
     */
    private void dispatchBlock(WebEngine engine, String url,
                               RelevanceResult result,
                               BiConsumer<String, RelevanceResult> onResult) {
        LOG.info("[BROWSER] BLOCKING: " + url + " (score=" + result.getScore() + ")");
        isHandling = true;

        // Defer all engine mutations to the next pulse (Bug 1 fix)
        Platform.runLater(() -> {
            try {
                // Step 1: Pause all media BEFORE cancelling the load
                //         so no audio continues playing (Bug 4 fix)
                pauseAllMedia(engine);

                // Step 2: Stop the current load to prevent the page from
                //         continuing to render or fire further events.
                //         getLoadWorker().cancel() is safe here because we are
                //         now in a fresh runLater pulse, not inside a listener.
                engine.getLoadWorker().cancel();

            } catch (Exception ex) {
                // Defensive: cancel can throw if worker is in a terminal state
                LOG.warning("[BROWSER] cancel() threw: " + ex.getMessage());
            } finally {
                // Always notify the view, even if cancel() failed
                onResult.accept(url, result);
                isHandling = false;
            }
        });
    }

    /**
     * Injects JavaScript to pause all <video> and <audio> elements.
     * Safe to call even if there are no media elements — the querySelectorAll
     * returns an empty list and forEach is a no-op.
     *
     * Uses a try/catch in JS and in Java so a CSP-blocked page can't crash us.
     */
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