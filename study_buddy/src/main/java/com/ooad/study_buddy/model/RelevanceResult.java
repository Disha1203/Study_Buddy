package com.ooad.study_buddy.model;

/**
 * GRASP - Information Expert: Encapsulates the outcome of a relevance check.
 * SRP: Immutable result carrier; no logic beyond verdict helpers.
 *
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIX
 * ═══════════════════════════════════════════════════════════════
 *
 *  BUG — BORDERLINE silently treated as ALLOWED in AiBrowserView
 *  ──────────────────────────────────────────────────────────────
 *  AiBrowserView.handleRelevanceResult() only checked isBlocked().
 *  BORDERLINE (score 0.40–0.65) fell into the else-branch and was
 *  treated as ALLOWED. This is wrong in two scenarios:
 *    a) Genuinely ambiguous pages that should be surfaced to the user.
 *    b) Python service down → RelevanceService returns BORDERLINE(0.5)
 *       as a network-failure fallback → a distraction page slips through.
 *  Fix: added isBorderline() so callers can handle this verdict explicitly.
 *  AiBrowserView now treats BORDERLINE the same as BLOCKED (show block page).
 * ═══════════════════════════════════════════════════════════════
 */
public class RelevanceResult {

    public enum Verdict { ALLOWED, BLOCKED, BORDERLINE }

    private final double score;
    private final Verdict verdict;
    private final String reason;

    private RelevanceResult(double score, Verdict verdict, String reason) {
        this.score   = score;
        this.verdict = verdict;
        this.reason  = reason;
    }

    // ── Factory helpers ───────────────────────────────────────────────────────

    public static RelevanceResult allowed(double score, String reason) {
        return new RelevanceResult(score, Verdict.ALLOWED, reason);
    }

    public static RelevanceResult blocked(double score, String reason) {
        return new RelevanceResult(score, Verdict.BLOCKED, reason);
    }

    public static RelevanceResult borderline(double score, String reason) {
        return new RelevanceResult(score, Verdict.BORDERLINE, reason);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public double  getScore()   { return score;   }
    public Verdict getVerdict() { return verdict; }
    public String  getReason()  { return reason;  }

    public boolean isBlocked()    { return verdict == Verdict.BLOCKED;    }

    // BUG FIX: added isBorderline() so AiBrowserView can handle this verdict
    // explicitly instead of silently falling through to ALLOWED.
    public boolean isBorderline() { return verdict == Verdict.BORDERLINE; }

    @Override
    public String toString() {
        return String.format("RelevanceResult{verdict=%s, score=%.2f, reason='%s'}",
                verdict, score, reason);
    }
}