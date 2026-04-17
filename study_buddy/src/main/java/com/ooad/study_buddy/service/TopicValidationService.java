package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.Topic;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * SERVICE — Topic Validation
 *
 * SRP : Only validates topic strings; knows nothing about timers or browsers.
 * OCP : New rules → add to VAGUE_TERMS or override isVague(); no class edits.
 * GRASP Information Expert: This class owns all knowledge about "good" topics.
 */
@Service
public class TopicValidationService {

    // ── Rule 1: known-vague single-word terms ─────────────────────────────────
    private static final Set<String> VAGUE_TERMS = Set.of(
            "ai", "coding", "stuff", "things", "misc", "general",
            "learning", "study", "work", "research", "internet",
            "technology", "tech", "science", "math", "programming",
            "computer", "engineering", "business", "finance"
    );

    // ── Rule 2: topic must be at least this long ───────────────────────────────
    private static final int MIN_LENGTH = 4;

    // ── Rule 3: must contain at least this many alphabetic characters ──────────
    private static final int MIN_ALPHA_CHARS = 4;

    // ── Rule 4: maximum consecutive-word repetition ───────────────────────────
    private static final int MAX_WORD_COUNT = 20;

    /**
     * Validates the raw topic string.
     *
     * @param rawTopic user-supplied string (may be null or blank)
     * @return a Topic object; callers check {@link Topic#isValid()}
     */
    public Topic validate(String rawTopic) {
        if (rawTopic == null || rawTopic.isBlank()) {
            return Topic.invalid(rawTopic == null ? "" : rawTopic,
                    "Topic cannot be empty.");
        }

        String trimmed = rawTopic.trim();

        // Rule: minimum length
        if (trimmed.length() < MIN_LENGTH) {
            return Topic.invalid(trimmed,
                    "Topic is too short. Please be more specific (e.g., 'Dijkstra's Algorithm').");
        }

        // Rule: must have enough alphabetic characters
        long alphaCount = trimmed.chars().filter(Character::isLetter).count();
        if (alphaCount < MIN_ALPHA_CHARS) {
            return Topic.invalid(trimmed,
                    "Topic must contain meaningful words.");
        }

        // Rule: too many words = wall of text, not a topic
        String[] words = trimmed.split("\\s+");
        if (words.length > MAX_WORD_COUNT) {
            return Topic.invalid(trimmed,
                    "Topic is too verbose. Keep it concise (e.g., 'Binary Search Trees in Java').");
        }

        // Rule: known vague single-word term
        if (words.length == 1 && VAGUE_TERMS.contains(trimmed.toLowerCase())) {
            return Topic.invalid(trimmed,
                    "'" + trimmed + "' is too vague. Add context (e.g., 'AI for medical imaging').");
        }

        return Topic.valid(trimmed);
    }
}
