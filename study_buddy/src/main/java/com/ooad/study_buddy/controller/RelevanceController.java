package com.ooad.study_buddy.controller;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.relevance.RelevanceChainFactory;
import com.ooad.study_buddy.relevance.RelevanceHandler;
import com.ooad.study_buddy.service.BlockingService;
import org.springframework.stereotype.Component;

/**
 * GRASP Controller: Coordinates relevance checking for a single URL.
 * Knows WHAT to call but not HOW each part works (low coupling).
 *
 * SRP : Only orchestrates; no business logic lives here.
 * DIP : Depends on RelevanceChainFactory and BlockingService abstractions.
 */
@Component
public class RelevanceController {

    private final RelevanceChainFactory chainFactory;
    private final BlockingService       blockingService;

    public RelevanceController(RelevanceChainFactory chainFactory,
                               BlockingService blockingService) {
        this.chainFactory    = chainFactory;
        this.blockingService = blockingService;
    }

    /**
     * Runs the full chain-of-responsibility check.
     *
     * @param topic   active study topic
     * @param content extracted page data
     * @return verdict + score
     */
    public RelevanceResult evaluate(String topic, ContentData content) {
        // Quick structural check first (no chain needed for explicit rules)
        BlockingService.Decision quick =
                blockingService.quickDecision(content.getUrl());

        switch (quick) {
            case ALLOW: return RelevanceResult.allowed(1.0, "Allowed by platform rule.");
            case BLOCK: return RelevanceResult.blocked(0.0, "Blocked by platform rule.");
            default:    break; // CHECK_RELEVANCE → fall through to chain
        }

        RelevanceHandler chain = chainFactory.buildChain();
        return chain.handle(topic, content);
    }
}
