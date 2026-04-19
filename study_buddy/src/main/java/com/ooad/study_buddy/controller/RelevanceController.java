package com.ooad.study_buddy.controller;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.relevance.RelevanceChainFactory;
import com.ooad.study_buddy.relevance.RelevanceHandler;
import com.ooad.study_buddy.service.BlockingService;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

/**
 * GRASP Controller: Coordinates relevance checking for a single URL.
 *
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIXES (vs previous version)
 * ═══════════════════════════════════════════════════════════════
 *
 *  BUG 1 — BORDERLINE treated as ALLOWED (irrelevant pages slip through)
 *  ───────────────────────────────────────────────────────────────────────
 *  ROOT CAUSE: RelevanceResult.isBlocked() returns false for BORDERLINE.
 *  AiBrowserView only calls result.isBlocked(), so BORDERLINE (score 0.4–0.65)
 *  and the fallback BORDERLINE(0.5) returned when Python is unreachable were
 *  BOTH treated as ALLOWED → irrelevant pages let through.
 *
 *  The spec says:
 *    >= 0.65 → ALLOWED
 *    0.40–0.65 → BORDERLINE  (grey zone)
 *    < 0.40 → BLOCKED
 *
 *  And the requirement says: "score < threshold → BLOCK".
 *  0.5 < 0.65 → BLOCK. BORDERLINE must become BLOCKED.
 *
 *  FIX: evaluate() now promotes any BORDERLINE result to BLOCKED with a
 *  descriptive reason so the user understands it was a borderline call.
 *  This preserves the Verdict enum in the chain (chain handlers still produce
 *  BORDERLINE internally), but by the time the result reaches AiBrowserView it
 *  is always either ALLOWED or BLOCKED — never ambiguous.
 *
 *  BUG 2 — Double quickDecision call
 *  ───────────────────────────────────
 *  ROOT CAUSE: BrowserController.stateListener called quickDecision, confirmed
 *  CHECK_RELEVANCE, then called evaluate() which called quickDecision AGAIN.
 *  Harmless for correctness but wastes a call and muddies the log.
 *
 *  FIX: New overload evaluateContent(topic, contentData) skips the
 *  quickDecision step — it assumes the caller has already confirmed
 *  CHECK_RELEVANCE. BrowserController uses this overload.
 *  The original evaluate() is kept for backward compatibility and for callers
 *  that don't pre-filter.
 *
 *  BUG 3 — No logging made debugging impossible
 *  ─────────────────────────────────────────────
 *  FIX: Added structured log lines at every decision branch so every URL's
 *  journey through the relevance pipeline is visible in the console.
 *
 * ═══════════════════════════════════════════════════════════════
 */
@Component
public class RelevanceController {

    private static final Logger LOG = Logger.getLogger(RelevanceController.class.getName());

    private final RelevanceChainFactory chainFactory;
    private final BlockingService       blockingService;

    public RelevanceController(RelevanceChainFactory chainFactory,
                               BlockingService blockingService) {
        this.chainFactory    = chainFactory;
        this.blockingService = blockingService;
    }

    // ── Primary entry point (used by BrowserController.stateListener) ─────────

    /**
     * Full evaluation: runs quickDecision first, then the chain if needed.
     * Promotes BORDERLINE → BLOCKED (see Bug 1 fix above).
     *
     * @param topic   active study topic
     * @param content extracted page data (URL must be set)
     * @return ALLOWED or BLOCKED — never BORDERLINE
     */
    public RelevanceResult evaluate(String topic, ContentData content) {
        String url = content.getUrl();

        // ── Quick structural check ──────────────────────────────────────────
        BlockingService.Decision quick = blockingService.quickDecision(url);
        LOG.info(String.format("[RELEVANCE] quickDecision('%s') = %s", url, quick));

        switch (quick) {
            case ALLOW:
                return RelevanceResult.allowed(1.0, "Allowed by platform/whitelist rule.");
            case BLOCK:
                return RelevanceResult.blocked(0.0, "Blocked by platform/blacklist rule.");
            default:
                break; // CHECK_RELEVANCE → fall through to chain
        }

        // ── Chain of Responsibility ─────────────────────────────────────────
        return evaluateContent(topic, content);
    }

    /**
     * Runs ONLY the Chain of Responsibility (no quickDecision).
     * Use this when the caller has already confirmed CHECK_RELEVANCE
     * (avoids the redundant double-call — Bug 2 fix).
     *
     * @param topic   active study topic
     * @param content extracted page data
     * @return ALLOWED or BLOCKED — never BORDERLINE (Bug 1 fix)
     */
    public RelevanceResult evaluateContent(String topic, ContentData content) {
        LOG.info(String.format("[RELEVANCE] Running chain for '%s' | topic='%s'",
                content.getUrl(), topic));

        RelevanceHandler chain = chainFactory.buildChain();
        RelevanceResult raw = chain.handle(topic, content);

        LOG.info(String.format("[RELEVANCE] Chain result: verdict=%s score=%.3f reason='%s'",
                raw.getVerdict(), raw.getScore(), raw.getReason()));

        // ── Promote BORDERLINE → BLOCKED ───────────────────────────────────
        // BORDERLINE means score is in [0.40, 0.65).
        // Per spec: score < 0.65 threshold → user should be blocked.
        // Allowing BORDERLINE pages defeats the purpose of the focus blocker.
        //
        // We KEEP the original score so the block page can show it accurately
        // (e.g. "Relevance score: 52%") but we change the verdict to BLOCKED.
        if (raw.getVerdict() == RelevanceResult.Verdict.BORDERLINE) {
            String reason = String.format(
                    "Borderline relevance (%.0f%%) — below the 65%% threshold. %s",
                    raw.getScore() * 100, raw.getReason());
            LOG.info("[RELEVANCE] BORDERLINE promoted to BLOCKED: " + reason);
            return RelevanceResult.blocked(raw.getScore(), reason);
        }

        return raw;
    }
}