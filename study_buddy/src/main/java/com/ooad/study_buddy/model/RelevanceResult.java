package com.ooad.study_buddy.model;

/**
 * GRASP - Information Expert: Encapsulates the outcome of a relevance check.
 * SRP: Immutable result carrier; no logic.
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

    public boolean isBlocked()  { return verdict == Verdict.BLOCKED; }

    @Override
    public String toString() {
        return String.format("RelevanceResult{verdict=%s, score=%.2f, reason='%s'}",
                verdict, score, reason);
    }
}
