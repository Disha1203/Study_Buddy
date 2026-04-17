package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;

/**
 * DESIGN PATTERN — Chain of Responsibility: Handler contract.
 *
 * SOLID OCP : New rules → new handler class, zero edits to existing ones.
 * SOLID DIP : All callers depend on this interface, not concrete handlers.
 * SOLID ISP : Minimal single-method interface.
 */
public interface RelevanceHandler {

    /**
     * Evaluate the request; either return a result or delegate to the next handler.
     *
     * @param topic   study topic
     * @param content extracted page data
     * @return non-null RelevanceResult
     */
    RelevanceResult handle(String topic, ContentData content);

    /**
     * Fluent chain builder.
     *
     * @param next handler to invoke when this one passes
     * @return {@code next} for further chaining
     */
    RelevanceHandler setNext(RelevanceHandler next);
}
