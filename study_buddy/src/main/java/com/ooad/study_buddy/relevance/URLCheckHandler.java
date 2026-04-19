package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;


/**
 * Chain link — Phase 1: URL-based quick check
 *
 * Logic:
 * 1. BLOCK if URL contains distraction keywords
 * 2. ALLOW if full topic is present in URL (word-safe match)
 * 3. Otherwise PASS to next handler
 *
 * SRP: Only performs fast URL-based filtering
 * OCP: Extend DISTRACTION_KEYWORDS without modifying logic
 */
public class URLCheckHandler extends AbstractRelevanceHandler {

    private static final List<String> DISTRACTION_KEYWORDS = Arrays.asList(
            "instagram", "facebook", "twitter", "tiktok",
            "snapchat", "twitch", "netflix", "hulu", "primevideo",
            "disneyplus", "9gag", "buzzfeed", "dailymotion",
            "pornhub", "onlyfans", "casino", "bet365", "gambling",
            "meme", "funny", "4chan", "imgur"
    );

    @Override
    public RelevanceResult handle(String topic, ContentData content) {

        String url = content.getUrl() == null ? "" : content.getUrl().toLowerCase(Locale.ROOT);

        // ── 1. BLOCK distractions ─────────────────────────────────────────────
        for (String keyword : DISTRACTION_KEYWORDS) {
            if (url.contains(keyword)) {
                return RelevanceResult.blocked(0.0,
                        "URL matches known distraction keyword: '" + keyword + "'");
            }
        }

        // ── Normalize inputs ──────────────────────────────────────────────────
        String cleanUrl = url.replaceAll("[^a-z0-9]", " ");
        String cleanTopic = topic.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", " ")
                .trim();

        // ── 2. ALLOW if full topic matches (safe word matching) ───────────────
        if (!cleanTopic.isBlank() && fullTopicMatch(cleanUrl, cleanTopic)) {
            return RelevanceResult.allowed(0.9,
                    "All topic keywords found in URL.");
        }

        // ── 3. PASS to next handler ───────────────────────────────────────────
        return passToNext(topic, content);
    }

    /**
     * Ensures ALL meaningful topic words exist in the URL as whole words.
     * Prevents false matches like:
     *  - "java" matching "javascript"
     *  - "net" matching "internet"
     */
    private boolean fullTopicMatch(String cleanUrl, String cleanTopic) {

        String[] topicWords = cleanTopic.split("\\s+");

        boolean matchedAtLeastOne = false;

        for (String word : topicWords) {

            // Ignore very short words (avoid noise like "ai", "of")
            if (word.length() < 3) continue;

            // Strict word boundary match
            if (!cleanUrl.matches(".*\\b" + word + "\\b.*")) {
                return false;
            }

            matchedAtLeastOne = true;
        }

        // Ensure at least one meaningful word matched
        return matchedAtLeastOne;
    }
}