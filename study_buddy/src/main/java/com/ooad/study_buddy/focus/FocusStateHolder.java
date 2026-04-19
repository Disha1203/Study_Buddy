package com.ooad.study_buddy.focus;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Shared mutable flag: true = focus block, false = break (no blocking).
 * Passed by reference so BrowserController always reads the current value.
 */
public class FocusStateHolder {

    private final AtomicBoolean focusMode = new AtomicBoolean(true);

    public void setFocusMode(boolean focus) {
        focusMode.set(focus);
    }

    public boolean isFocusMode() {
        return focusMode.get();
    }
}