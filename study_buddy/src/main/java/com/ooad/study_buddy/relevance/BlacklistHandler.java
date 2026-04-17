package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.service.BlockingService;

import java.net.URI;

/**
 * Chain link 2 — Blacklist check.
 * If the domain is explicitly blacklisted, short-circuit with BLOCK.
 */
public class BlacklistHandler extends AbstractRelevanceHandler {

    private final BlockingService blockingService;

    public BlacklistHandler(BlockingService blockingService) {
        this.blockingService = blockingService;
    }

    @Override
    public RelevanceResult handle(String topic, ContentData content) {
        String domain = extractDomain(content.getUrl());
        if (blockingService.isBlacklisted(domain)) {
            return RelevanceResult.blocked(0.0, "Domain is blacklisted: " + domain);
        }
        return passToNext(topic, content);
    }

    private String extractDomain(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }
}
