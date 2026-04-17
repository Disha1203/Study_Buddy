package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.service.RelevanceService;

/**
 * Chain link 4 — Phase 2: deep content relevance check via Python embedding API.
 *
 * This is the last handler; it always produces a verdict.
 * SRP : Only responsible for invoking RelevanceService and returning the result.
 */
public class ContentCheckHandler extends AbstractRelevanceHandler {

    private final RelevanceService relevanceService;

    public ContentCheckHandler(RelevanceService relevanceService) {
        this.relevanceService = relevanceService;
    }

    @Override
    public RelevanceResult handle(String topic, ContentData content) {
        // Full semantic check — always returns a result (no pass-to-next needed)
        return relevanceService.check(topic, content);
    }
}
