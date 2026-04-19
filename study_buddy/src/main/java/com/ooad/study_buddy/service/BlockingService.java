package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.SiteMetadata;
import com.ooad.study_buddy.repository.SiteMetadataRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * SERVICE — Blocking rules & platform-specific logic
 *
 * CHANGE: YouTube homepage ("/") now returns ALLOW instead of CHECK_RELEVANCE.
 *         Users need to navigate from the YouTube homepage; blocking it
 *         immediately is confusing. Only individual videos (/watch), search
 *         results (/results), and Shorts (/shorts) are evaluated or blocked.
 */
@Service
public class BlockingService {

    private static final Logger LOG = Logger.getLogger(BlockingService.class.getName());

    private final SiteMetadataRepository ruleRepo;

    public BlockingService(SiteMetadataRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public enum Decision { ALLOW, BLOCK, CHECK_RELEVANCE }

    /**
     * Fast structural decision — called before any content extraction.
     *
     * Priority order:
     *   1. Whitelist  → ALLOW  (absolute override)
     *   2. Blacklist  → BLOCK  (absolute override)
     *   3. Known distraction domains → BLOCK
     *   4. Platform path rules (YouTube, Reddit)
     *   5. Everything else → CHECK_RELEVANCE
     */
    public Decision quickDecision(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return Decision.ALLOW;

        String domain = extractDomain(rawUrl);
        String path   = extractPath(rawUrl);

        // 1. Whitelist — absolute ALLOW (even known distractions can be whitelisted)
        if (isWhitelisted(domain)) {
            LOG.fine("[BLOCKING] ALLOW (whitelist): " + rawUrl);
            return Decision.ALLOW;
        }

        // 2. Explicit blacklist — absolute BLOCK
        if (isBlacklisted(domain)) {
            LOG.info("[BLOCKING] BLOCK (blacklist): " + rawUrl);
            return Decision.BLOCK;
        }

        // 3. Known distraction social / streaming / entertainment domains
        if (isKnownDistraction(domain)) {
            LOG.info("[BLOCKING] BLOCK (known distraction): " + rawUrl);
            return Decision.BLOCK;
        }

        // 4. Platform-specific path rules
        if (isYoutube(domain)) {
            Decision d = youtubeDecision(path);
            LOG.info("[BLOCKING] YouTube path=" + path + " → " + d);
            return d;
        }
        if (isReddit(domain)) {
            Decision d = redditDecision(path);
            LOG.info("[BLOCKING] Reddit path=" + path + " → " + d);
            return d;
        }

        LOG.fine("[BLOCKING] CHECK_RELEVANCE: " + rawUrl);
        return Decision.CHECK_RELEVANCE;
    }

    // ── Whitelist / Blacklist management ─────────────────────────────────────

    public void whitelist(String domain, String notes) {
        upsert(normalizeDomain(domain), SiteMetadata.RuleType.WHITELIST, notes);
    }

    public void blacklist(String domain, String notes) {
        upsert(normalizeDomain(domain), SiteMetadata.RuleType.BLACKLIST, notes);
    }

    public void removeDomain(String domain) {
        String normalized = normalizeDomain(domain);
        ruleRepo.findByDomain(normalized).ifPresent(ruleRepo::delete);
    }

    public boolean isWhitelisted(String domain) {
        return ruleRepo.existsByDomainAndRuleType(
                normalizeDomain(domain), SiteMetadata.RuleType.WHITELIST);
    }

    public boolean isBlacklisted(String domain) {
        return ruleRepo.existsByDomainAndRuleType(
                normalizeDomain(domain), SiteMetadata.RuleType.BLACKLIST);
    }

    public List<String> getAllWhitelisted() {
        return ruleRepo.findAll().stream()
                .filter(m -> m.getRuleType() == SiteMetadata.RuleType.WHITELIST)
                .map(SiteMetadata::getDomain)
                .sorted()
                .toList();
    }

    public List<String> getAllBlacklisted() {
        return ruleRepo.findAll().stream()
                .filter(m -> m.getRuleType() == SiteMetadata.RuleType.BLACKLIST)
                .map(SiteMetadata::getDomain)
                .sorted()
                .toList();
    }

    // ── Platform rules ────────────────────────────────────────────────────────

    /**
     * YouTube path rules:
     *   /           → ALLOW   (homepage — user needs a starting point)
     *   /shorts/*   → BLOCK   (Shorts: always distraction)
     *   /watch      → CHECK_RELEVANCE (video evaluated against topic)
     *   /results    → CHECK_RELEVANCE (search results)
     *   anything else → CHECK_RELEVANCE
     *
     * FIX: "/" was previously CHECK_RELEVANCE which caused the YouTube
     * homepage to be blocked on first visit (empty-page content scores 0).
     */
    private Decision youtubeDecision(String path) {
        if (path == null || path.isBlank()) return Decision.ALLOW;

        // Normalize: remove trailing slash unless it IS the root
        String p = (path.length() > 1 && path.endsWith("/"))
                ? path.substring(0, path.length() - 1) : path;

        // Homepage — always allow (let user navigate to a video first)
        if (p.equals("/") || p.isBlank()) return Decision.ALLOW;

        // Shorts — always block, no content check needed
        if (p.startsWith("/shorts")) return Decision.BLOCK;

        if (p.startsWith("/watch"))   return Decision.CHECK_RELEVANCE;
        if (p.startsWith("/results")) return Decision.CHECK_RELEVANCE;

        // Channel pages, playlists, etc. — check relevance
        return Decision.CHECK_RELEVANCE;
    }

    /**
     * Reddit path rules:
     *   /                          → BLOCK (infinite scroll / front page)
     *   /r/sub/comments/id/slug    → CHECK_RELEVANCE (actual post)
     *   /r/subreddit               → CHECK_RELEVANCE (may be on-topic)
     *   /search                    → CHECK_RELEVANCE
     *   anything else              → BLOCK
     */
    private Decision redditDecision(String path) {
        if (path == null || path.isBlank() || path.equals("/")) return Decision.BLOCK;

        if (path.startsWith("/r/") && path.contains("/comments/")) return Decision.CHECK_RELEVANCE;
        if (path.startsWith("/search"))  return Decision.CHECK_RELEVANCE;
        if (path.startsWith("/r/"))      return Decision.CHECK_RELEVANCE;
        return Decision.BLOCK;
    }

    // ── Known-distraction domains ─────────────────────────────────────────────

    private static final Set<String> DISTRACTION_DOMAINS = Set.of(
            "instagram.com", "facebook.com", "twitter.com", "x.com",
            "tiktok.com", "snapchat.com", "twitch.tv",
            "netflix.com", "hulu.com", "primevideo.com", "disneyplus.com",
            "9gag.com", "buzzfeed.com", "dailymotion.com",
            "4chan.org", "imgur.com"
    );

    private boolean isKnownDistraction(String domain) {
        return DISTRACTION_DOMAINS.contains(normalizeDomain(domain));
    }

    // ── Utility helpers ───────────────────────────────────────────────────────

    private String extractDomain(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }

    private String extractPath(String url) {
        try { return URI.create(url).getPath(); }
        catch (Exception e) { return ""; }
    }

    public String normalizeDomain(String domain) {
        if (domain == null) return "";
        return domain.toLowerCase().replaceFirst("^www\\.", "");
    }

    private boolean isYoutube(String domain) {
        String d = normalizeDomain(domain);
        return "youtube.com".equals(d) || "m.youtube.com".equals(d) || "youtu.be".equals(d);
    }

    private boolean isReddit(String domain) {
        String d = normalizeDomain(domain);
        return "reddit.com".equals(d) || "old.reddit.com".equals(d) || "new.reddit.com".equals(d);
    }

    private void upsert(String domain, SiteMetadata.RuleType type, String notes) {
        Optional<SiteMetadata> existing = ruleRepo.findByDomain(domain);
        SiteMetadata record = existing.orElse(new SiteMetadata(domain, type, notes));
        ruleRepo.save(record);
    }
}