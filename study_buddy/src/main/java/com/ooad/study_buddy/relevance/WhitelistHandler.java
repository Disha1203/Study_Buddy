package com.ooad.study_buddy.relevance;

import com.ooad.study_buddy.model.ContentData;
import com.ooad.study_buddy.model.RelevanceResult;
import com.ooad.study_buddy.service.BlockingService;

import java.net.URI;

/**
 * Chain link 1 — Whitelist check.
 * If the domain is whitelisted, short-circuit with ALLOW immediately.
 */
public class WhitelistHandler extends AbstractRelevanceHandler {

    private final BlockingService blockingService;

    public WhitelistHandler(BlockingService blockingService) {
        this.blockingService = blockingService;
    }

    @Override
    public RelevanceResult handle(String topic, ContentData content) {
        String domain = extractDomain(content.getUrl());
        if (blockingService.isWhitelisted(domain)) {
            return RelevanceResult.allowed(1.0, "Domain is whitelisted: " + domain);
        }
        return passToNext(topic, content);
    }

    private String extractDomain(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }
}
