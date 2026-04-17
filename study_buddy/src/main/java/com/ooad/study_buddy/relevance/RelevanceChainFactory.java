package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.service.BlockingService;
import com.ooad.study_buddy.service.RelevanceService;
import org.springframework.stereotype.Component;

/**
 * Factory that assembles the Chain of Responsibility.
 *
 * Chain order:
 *   WhitelistHandler → BlacklistHandler → URLCheckHandler → ContentCheckHandler
 *
 * GRASP Creator: Owns construction of the chain; callers just call handle().
 * OCP: Inserting a new step = creating a new handler + one line here.
 */
@Component
public class RelevanceChainFactory {

    private final BlockingService  blockingService;
    private final RelevanceService relevanceService;

    public RelevanceChainFactory(BlockingService blockingService,
                                  RelevanceService relevanceService) {
        this.blockingService  = blockingService;
        this.relevanceService = relevanceService;
    }

    /**
     * Builds and returns the head of a fresh chain.
     * A fresh chain is created per-request so handlers remain stateless.
     */
    public RelevanceHandler buildChain() {
        RelevanceHandler whitelist = new WhitelistHandler(blockingService);
        RelevanceHandler blacklist = new BlacklistHandler(blockingService);
        RelevanceHandler urlCheck  = new URLCheckHandler();
        RelevanceHandler content   = new ContentCheckHandler(relevanceService);

        whitelist.setNext(blacklist)
                 .setNext(urlCheck)
                 .setNext(content);

        return whitelist;
    }
}
