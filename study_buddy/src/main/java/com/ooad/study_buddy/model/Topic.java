package com.ooad.study_buddy.model;

/**
 * GRASP - Information Expert: Holds topic data and vagueness metadata.
 * SRP: Pure data holder, no logic.
 */
public class Topic {

    private final String rawText;
    private final boolean valid;
    private final String validationError;

    private Topic(String rawText, boolean valid, String validationError) {
        this.rawText = rawText;
        this.valid = valid;
        this.validationError = validationError;
    }

    public static Topic valid(String text) {
        return new Topic(text, true, null);
    }

    public static Topic invalid(String text, String reason) {
        return new Topic(text, false, reason);
    }

    public String getRawText()        { return rawText; }
    public boolean isValid()          { return valid; }
    public String getValidationError(){ return validationError; }

    @Override
    public String toString() {
        return "Topic{rawText='" + rawText + "', valid=" + valid + "}";
    }
}
