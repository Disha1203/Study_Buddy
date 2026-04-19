package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.Topic;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * SERVICE — Topic Validation (Improved Gibberish Detection)
 *
 * Fix:
 *  - Stronger word-level gibberish detection
 *
 * Everything else unchanged.
 */
@Service
public class TopicValidationService {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final Set<String> VAGUE_TERMS = Set.of(
            "ai", "coding", "stuff", "things", "misc", "general",
            "learning", "study", "work", "research", "internet",
            "technology", "tech", "science", "math", "programming",
            "computer", "engineering", "business", "finance",
            "sports", "news", "entertainment", "music", "art",
            "games", "gaming", "social", "media", "content",
            "random", "other", "test", "testing", "project"
    );

    private static final Set<String> NON_TOPIC_VERBS = Set.of(
            "watch", "see", "open", "play", "go", "check", "browse"
    );

    private static final Set<String> STOPWORDS = Set.of(
            "the", "is", "and", "of", "to", "in", "on", "for", "a", "an"
    );

    private static final int MIN_LENGTH    = 5;
    private static final int MIN_ALPHA     = 4;
    private static final int MIN_WORDS     = 2;
    private static final int MAX_WORDS     = 20;
    private static final int MAX_WORD_REPS = 3;

    // ── Public API ─────────────────────────────────────────────────────────────

    public Topic validate(String rawTopic) {

        if (rawTopic == null || rawTopic.isBlank()) {
            return Topic.invalid("", "Topic cannot be empty.");
        }

        String trimmed = rawTopic.trim();
        String[] words = trimmed.split("\\s+");

        // Rule 1: minimum length
        if (trimmed.length() < MIN_LENGTH) {
            return Topic.invalid(trimmed,
                    "Topic is too short — please be more specific (e.g. 'Dijkstra's Algorithm').");
        }

        // Rule 2: alphabetic characters
        long alphaCount = trimmed.chars().filter(Character::isLetter).count();
        if (alphaCount < MIN_ALPHA) {
            return Topic.invalid(trimmed,
                    "Topic must contain meaningful words, not just numbers or symbols.");
        }

        // Rule 3: max words
        if (words.length > MAX_WORDS) {
            return Topic.invalid(trimmed,
                    "Topic is too verbose — keep it concise.");
        }

        // Rule 4: minimum words
        if (words.length < MIN_WORDS) {
            return Topic.invalid(trimmed,
                    "Topic should contain at least 2 meaningful words.");
        }

        // 🔥 FIXED RULE: Strong gibberish detection
        if (looksLikeGibberish(words)) {
            return Topic.invalid(trimmed,
                    "Topic doesn't look meaningful.");
        }

        // Rule 5: action phrase
        if (isActionPhrase(words)) {
            return Topic.invalid(trimmed,
                    "Topic should describe what you study, not an action.");
        }

        // Rule 6: stopword-only
        if (onlyStopwords(words)) {
            return Topic.invalid(trimmed,
                    "Topic must contain meaningful keywords.");
        }

        // Rule 7: single-word vague
        if (words.length == 1 && VAGUE_TERMS.contains(trimmed.toLowerCase())) {
            return Topic.invalid(trimmed,
                    "'" + trimmed + "' is too vague — add context.");
        }

        // Rule 8: multi-word vague
        if (isTooGeneric(words)) {
            return Topic.invalid(trimmed,
                    "Topic is too generic — add more specific context.");
        }

        // Rule 9: repetition
        if (hasExcessiveRepetition(words)) {
            return Topic.invalid(trimmed,
                    "Topic repeats the same word too many times.");
        }

        return Topic.valid(trimmed);
    }

    // ── Helper Methods ─────────────────────────────────────────────────────────

    /**
     * 🔥 Improved gibberish detection (word-level)
     */
    private boolean looksLikeGibberish(String[] words) {
        int suspiciousWords = 0;

        for (String word : words) {
            String w = word.toLowerCase();

            if (w.length() <= 2) continue;

            // no vowels → suspicious
            if (!w.matches(".*[aeiou].*")) {
                suspiciousWords++;
                continue;
            }

            // long word with no common English patterns
            if (w.length() > 7 && !w.matches(".*(tion|ing|ed|er|al).*")) {
                suspiciousWords++;
            }
        }

        return suspiciousWords >= Math.max(1, words.length / 2);
    }

    private boolean isActionPhrase(String[] words) {
        return NON_TOPIC_VERBS.contains(words[0].toLowerCase());
    }

    private boolean onlyStopwords(String[] words) {
        return Arrays.stream(words)
                .allMatch(w -> STOPWORDS.contains(w.toLowerCase()));
    }

    private boolean isTooGeneric(String[] words) {
        int vagueCount = 0;
        for (String w : words) {
            if (VAGUE_TERMS.contains(w.toLowerCase())) vagueCount++;
        }
        return vagueCount == words.length;
    }

    private boolean hasExcessiveRepetition(String[] words) {
        Map<String, Integer> freq = new HashMap<>();
        for (String w : words) {
            String lw = w.toLowerCase();
            freq.put(lw, freq.getOrDefault(lw, 0) + 1);
            if (freq.get(lw) > MAX_WORD_REPS) return true;
        }
        return false;
    }
}