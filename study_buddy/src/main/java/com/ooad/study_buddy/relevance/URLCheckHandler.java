package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Chain link 3 — Phase 1: fast URL-keyword check.
 *
 * Looks for obvious distraction domains/paths without any API call.
 * OCP: extend DISTRACTION_KEYWORDS without touching other handlers.
 */
public class URLCheckHandler extends AbstractRelevanceHandler {

    private static final List<String> DISTRACTION_KEYWORDS = Arrays.asList(
            "instagram", "facebook", "twitter", "x.com", "tiktok",
            "snapchat", "twitch", "netflix", "hulu", "primevideo",
            "disneyplus", "9gag", "buzzfeed", "dailymotion",
            "pornhub", "onlyfans", "casino", "bet365", "gambling",
            "meme", "funny", "lol", "wtf", "4chan", "imgur"
    );

    @Override
    public RelevanceResult handle(String topic, ContentData content) {
        String url = content.getUrl() == null ? "" : content.getUrl().toLowerCase(Locale.ROOT);

        for (String keyword : DISTRACTION_KEYWORDS) {
            if (url.contains(keyword)) {
                return RelevanceResult.blocked(0.0,
                        "URL matches known distraction keyword: '" + keyword + "'");
            }
        }

        // Check if any topic words appear in the URL — boosts confidence
        String[] topicWords = topic.toLowerCase(Locale.ROOT).split("\\s+");
        for (String word : topicWords) {
            if (word.length() >= 4 && url.contains(word)) {
                // Topic keyword in URL is a strong relevance signal — skip deep check
                return RelevanceResult.allowed(0.8,
                        "Topic keyword '" + word + "' found in URL.");
            }
        }

        return passToNext(topic, content);
    }
}
