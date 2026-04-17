package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;

/**
 * Chain of Responsibility — Abstract base handler.
 * Handles "pass to next" boilerplate so concrete handlers stay focused.
 * SOLID SRP: Only manages chain linkage; subclasses own check logic.
 */
public abstract class AbstractRelevanceHandler implements RelevanceHandler {

    private RelevanceHandler next;

    @Override
    public RelevanceHandler setNext(RelevanceHandler next) {
        this.next = next;
        return next;
    }

    /**
     * Delegates to the next handler in the chain, or returns ALLOWED if none.
     */
    protected RelevanceResult passToNext(String topic, ContentData content) {
        if (next != null) {
            return next.handle(topic, content);
        }
        // End of chain with no block → allow by default
        return RelevanceResult.allowed(1.0, "Passed all checks.");
    }
}
