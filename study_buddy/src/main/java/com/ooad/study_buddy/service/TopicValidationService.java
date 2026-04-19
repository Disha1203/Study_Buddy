package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.Topic;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * SERVICE — Topic Validation  (fixed / extended)
 *
 * KEY FIXES:
 *  1. Expanded VAGUE_TERMS — covers more one-word non-topics.
 *  2. Added Rule 5: rejects topics that are ONLY special chars / numbers.
 *  3. Added Rule 6: rejects topics that repeat the same word many times
 *     (e.g. "java java java java java").
 *  4. MIN_LENGTH raised to 5 to reject single-letter + space combos.
 *  5. Method is now used by HomepageView BEFORE creating the FocusSession.
 *
 * SRP : Only validates topic strings.
 * OCP : New rules → add to VAGUE_TERMS or add a private check method.
 * GRASP Information Expert: owns all knowledge about what a "good" topic is.
 */

@Service
public class TopicValidationService {

    // ── Constants ──────────────────────────────────────────────────────────────

    private static final Set<String> VAGUE_TERMS = Set.of(
            "ai", "coding", "stuff", "things", "misc", "general",
            "learning", "study", "work", "research", "internet",
            "technology", "tech", "science", "math", "programming",
            "computer", "engineering", "business", "finance",
            "sports", "news", "entertainment", "music", "art",
            "games", "gaming", "social", "media", "content",
            "random", "other", "test", "testing", "project"
    );

    private static final int MIN_LENGTH    = 5;
    private static final int MIN_ALPHA     = 4;
    private static final int MAX_WORDS     = 20;
    private static final int MAX_WORD_REPS = 3; // same word may appear at most this many times

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Validates the raw topic string.
     *
     * @param rawTopic user-supplied string (may be null or blank)
     * @return a Topic; callers check {@link Topic#isValid()}
     */
    public Topic validate(String rawTopic) {
        if (rawTopic == null || rawTopic.isBlank())
            return Topic.invalid("", "Topic cannot be empty.");

        String trimmed = rawTopic.trim();

        // Rule 1: minimum length
        if (trimmed.length() < MIN_LENGTH)
            return Topic.invalid(trimmed,
                    "Topic is too short — please be more specific (e.g. 'Dijkstra's Algorithm').");

        // Rule 2: must have enough alphabetic characters
        long alphaCount = trimmed.chars().filter(Character::isLetter).count();
        if (alphaCount < MIN_ALPHA)
            return Topic.invalid(trimmed,
                    "Topic must contain meaningful words, not just numbers or symbols.");

        // Rule 3: too many words
        String[] words = trimmed.split("\\s+");
        if (words.length > MAX_WORDS)
            return Topic.invalid(trimmed,
                    "Topic is too verbose — keep it concise (e.g. 'Binary Search Trees in Java').");

        // Rule 4: known vague single-word term
        if (words.length == 1 && VAGUE_TERMS.contains(trimmed.toLowerCase()))
            return Topic.invalid(trimmed,
                    "'" + trimmed + "' is too vague — add context (e.g. 'AI for medical imaging').");

        // Rule 5: repetition of the same word
        java.util.Map<String, Long> freq = new java.util.HashMap<>();
        for (String w : words) {
            String lw = w.toLowerCase();
            freq.merge(lw, 1L, Long::sum);
        }
        for (java.util.Map.Entry<String, Long> e : freq.entrySet()) {
            if (e.getValue() > MAX_WORD_REPS)
                return Topic.invalid(trimmed,
                        "Topic seems to repeat '" + e.getKey() + "' too many times.");
        }

        return Topic.valid(trimmed);
    }
}