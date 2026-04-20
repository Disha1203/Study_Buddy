package com.ooad.study_buddy.focus;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Shared mutable state: tracks FOCUS, BREAK, or BUFFER mode.
 *
 * CHANGE: Replaced the simple boolean with a 3-state enum so that
 * BrowserController and BlockingService can distinguish between:
 *   FOCUS  — full blocking active
 *   BREAK  — no blocking (whitelisted rest period)
 *   BUFFER — 2-minute grace window after a block page, no blocking
 *
 * This preserves the existing isFocusMode() contract for callers
 * that only care about focus vs. non-focus.
 */
public class FocusStateHolder {

    public enum Mode { FOCUS, BREAK, BUFFER }

    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.FOCUS);

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setFocusMode(boolean focus) {
        mode.set(focus ? Mode.FOCUS : Mode.BREAK);
    }

    public void setMode(Mode newMode) {
        mode.set(newMode);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Mode getMode() {
        return mode.get();
    }

    /** True only during FOCUS — used by existing callers unchanged. */
    public boolean isFocusMode() {
        return mode.get() == Mode.FOCUS;
    }

    /** True when any non-blocking mode is active (BREAK or BUFFER). */
    public boolean isBlockingDisabled() {
        Mode m = mode.get();
        return m == Mode.BREAK || m == Mode.BUFFER;
    }
}