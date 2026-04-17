package com.ooad.study_buddy.model;

/**
 * GRASP - Information Expert: Carries all extracted page content.
 * SRP: Data-only object; built by ContentExtractionService.
 */
public class ContentData {

    private final String url;
    private final String title;
    private final String metaDescription;
    private final String openGraphTitle;
    private final String openGraphDescription;
    private final String firstHeading;
    private final String visibleText;

    public ContentData(
            String url,
            String title,
            String metaDescription,
            String openGraphTitle,
            String openGraphDescription,
            String firstHeading,
            String visibleText) {
        this.url                = url;
        this.title              = title;
        this.metaDescription    = metaDescription;
        this.openGraphTitle     = openGraphTitle;
        this.openGraphDescription = openGraphDescription;
        this.firstHeading       = firstHeading;
        this.visibleText        = visibleText;
    }

    public String getUrl()                  { return url; }
    public String getTitle()               { return title; }
    public String getMetaDescription()     { return metaDescription; }
    public String getOpenGraphTitle()      { return openGraphTitle; }
    public String getOpenGraphDescription(){ return openGraphDescription; }
    public String getFirstHeading()        { return firstHeading; }
    public String getVisibleText()         { return visibleText; }

    /**
     * Builds a combined text blob for relevance scoring.
     * Prioritises structured fields; falls back to body text.
     */
    public String toCombinedText() {
        StringBuilder sb = new StringBuilder();
        append(sb, title);
        append(sb, openGraphTitle);
        append(sb, openGraphDescription);
        append(sb, metaDescription);
        append(sb, firstHeading);
        if (visibleText != null && !visibleText.isBlank()) {
            // Limit to first 800 chars to keep API calls lean
            sb.append(visibleText, 0, Math.min(visibleText.length(), 800));
        }
        return sb.toString().trim();
    }

    private void append(StringBuilder sb, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(value).append(". ");
        }
    }
}
