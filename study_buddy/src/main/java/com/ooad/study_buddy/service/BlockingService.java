package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.SiteMetadata;
import com.ooad.study_buddy.repository.SiteMetadataRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

/**
 * SERVICE — Blocking rules & platform-specific logic
 *
 * SRP  : Knows only about whitelist / blacklist and platform path rules.
 * OCP  : Add a new platform by implementing a PlatformRule (inner interface).
 * DIP  : Depends on SiteMetadataRepository interface, not H2 directly.
 */
@Service
public class BlockingService {

    private final SiteMetadataRepository ruleRepo;

    public BlockingService(SiteMetadataRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public enum Decision { ALLOW, BLOCK, CHECK_RELEVANCE }

    /**
     * Fast structural decision before any relevance check.
     *
     * @param rawUrl URL string of the page being loaded
     * @return ALLOW / BLOCK / CHECK_RELEVANCE
     */
    public Decision quickDecision(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return Decision.ALLOW;

        String domain = extractDomain(rawUrl);
        String path   = extractPath(rawUrl);

        // 1. Whitelist takes priority
        if (isWhitelisted(domain)) return Decision.ALLOW;

        // 2. Blacklist is next fastest
        if (isBlacklisted(domain)) return Decision.BLOCK;

        // 3. Platform-specific rules
        if (isYoutube(domain))  return youtubeDecision(path);
        if (isReddit(domain))   return redditDecision(path);

        return Decision.CHECK_RELEVANCE;
    }

    // ── Whitelist / Blacklist management ─────────────────────────────────────

    public void whitelist(String domain, String notes) {
        upsert(domain, SiteMetadata.RuleType.WHITELIST, notes);
    }

    public void blacklist(String domain, String notes) {
        upsert(domain, SiteMetadata.RuleType.BLACKLIST, notes);
    }

    public boolean isWhitelisted(String domain) {
        return ruleRepo.existsByDomainAndRuleType(
                normalizeDomain(domain), SiteMetadata.RuleType.WHITELIST);
    }

    public boolean isBlacklisted(String domain) {
        return ruleRepo.existsByDomainAndRuleType(
                normalizeDomain(domain), SiteMetadata.RuleType.BLACKLIST);
    }

    // ── Platform rules ────────────────────────────────────────────────────────

    private Decision youtubeDecision(String path) {
        if (path == null || path.equals("/") || path.isBlank()) return Decision.ALLOW; // homepage
        if (path.startsWith("/shorts"))   return Decision.BLOCK;        // block Shorts
        if (path.startsWith("/watch"))    return Decision.CHECK_RELEVANCE; // video → deep check
        if (path.startsWith("/results"))  return Decision.CHECK_RELEVANCE; // search
        return Decision.CHECK_RELEVANCE;
    }

    private Decision redditDecision(String path) {
        if (path == null || path.equals("/") || path.isBlank()) return Decision.BLOCK; // block homepage
        if (path.startsWith("/r/") && path.contains("/comments/")) return Decision.CHECK_RELEVANCE; // post
        if (path.startsWith("/search")) return Decision.CHECK_RELEVANCE; // search
        if (path.startsWith("/r/"))     return Decision.CHECK_RELEVANCE; // subreddit
        return Decision.BLOCK;
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    private String extractDomain(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private String extractPath(String url) {
        try {
            return URI.create(url).getPath();
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizeDomain(String domain) {
        if (domain == null) return "";
        return domain.toLowerCase().replaceFirst("^www\\.", "");
    }

    private boolean isYoutube(String domain) {
        String d = normalizeDomain(domain);
        return "youtube.com".equals(d) || "m.youtube.com".equals(d);
    }

    private boolean isReddit(String domain) {
        String d = normalizeDomain(domain);
        return "reddit.com".equals(d) || "old.reddit.com".equals(d);
    }

    private void upsert(String domain, SiteMetadata.RuleType type, String notes) {
        String normalized = normalizeDomain(domain);
        Optional<SiteMetadata> existing = ruleRepo.findByDomain(normalized);
        SiteMetadata record = existing.orElse(new SiteMetadata(normalized, type, notes));
        ruleRepo.save(record);
    }
}
