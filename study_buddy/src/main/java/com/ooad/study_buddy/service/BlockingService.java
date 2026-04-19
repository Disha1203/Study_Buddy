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
 * ═══════════════════════════════════════════════════════════════
 *  BUG FIXES (vs previous version)
 * ═══════════════════════════════════════════════════════════════
 *
 *  BUG 1 — YouTube Shorts not consistently blocked
 *  ─────────────────────────────────────────────────
 *  ROOT CAUSE: The path check was case-sensitive and didn't account for
 *  "/shorts/" (with trailing slash + video ID). The check was:
 *    if (p.startsWith("/shorts"))
 *  which correctly catches "/shorts" and "/shorts/abc123", BUT only after
 *  the normalization step. The normalization removed trailing slash from
 *  the root path only when length > 1, so "/shorts/" (slash on a top-level
 *  Shorts listing) was being normalized to "/shorts" — that was fine.
 *  The actual problem was a URL like "https://youtube.com/shorts" (no trailing
 *  slash) which URI.getPath() returns as "/shorts" — this WAS being caught.
 *
 *  Re-investigation: the real Shorts blocking failure was that the URL
 *  sometimes arrives as "https://www.youtube.com/shorts/ABC123?feature=share"
 *  and the query string is stripped by URI.getPath() correctly. This path
 *  SHOULD have been caught. The failure was actually in the CACHING: if
 *  the homepage "youtube.com/" was visited first and cached as ALLOW, then
 *  the locationListener saw the Shorts URL, found it NOT in cache, ran
 *  quickDecision → BLOCK, tried to cancel(), but cancel() was called
 *  synchronously causing the crash → the block never showed.
 *
 *  FIX: The real fix is in BrowserController (deferred cancel). This class
 *  is already correct for Shorts. Added explicit logging so Shorts blocks
 *  are visible in the console.
 *
 *  BUG 2 — normalizeDomain not called consistently in whitelist/blacklist
 *  ────────────────────────────────────────────────────────────────────────
 *  ROOT CAUSE: whitelist() and blacklist() called normalizeDomain() before
 *  calling upsert(), but upsert() also re-applied normalizeDomain() (double
 *  normalization was harmless but confusing). isWhitelisted/isBlacklisted
 *  also called normalizeDomain() on their input. All fine — no bug here,
 *  but cleaned up for clarity.
 *
 *  BUG 3 — BlockingServiceAdditions.java was a separate file
 *  ────────────────────────────────────────────────────────────
 *  ROOT CAUSE: Extra methods needed by WhitelistManagerView were in a
 *  separate stub file and never merged into the actual service.
 *
 *  FIX: removeDomain(), getAllWhitelisted(), getAllBlacklisted() are merged
 *  into this class.
 *
 * ═══════════════════════════════════════════════════════════════
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

    /**
     * Removes any whitelist or blacklist entry for the given domain.
     * (Merged from BlockingServiceAdditions.java)
     */
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

    /**
     * Returns all whitelisted domains, sorted alphabetically.
     * (Merged from BlockingServiceAdditions.java)
     */
    public List<String> getAllWhitelisted() {
        return ruleRepo.findAll().stream()
                .filter(m -> m.getRuleType() == SiteMetadata.RuleType.WHITELIST)
                .map(SiteMetadata::getDomain)
                .sorted()
                .toList();
    }

    /**
     * Returns all blacklisted domains, sorted alphabetically.
     * (Merged from BlockingServiceAdditions.java)
     */
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
     *   /shorts/*   → BLOCK  (Shorts: always distraction)
     *   /watch      → CHECK_RELEVANCE (video title checked against topic)
     *   /results    → CHECK_RELEVANCE (search results)
     *   /           → CHECK_RELEVANCE (homepage — not unconditionally allowed)
     *   anything else → CHECK_RELEVANCE
     */
    private Decision youtubeDecision(String path) {
        if (path == null || path.isBlank()) return Decision.CHECK_RELEVANCE;

        // Normalize: remove trailing slash unless it IS the root
        String p = (path.length() > 1 && path.endsWith("/"))
                ? path.substring(0, path.length() - 1) : path;

        // Shorts — always block, no content check needed
        if (p.startsWith("/shorts")) return Decision.BLOCK;

        if (p.startsWith("/watch"))   return Decision.CHECK_RELEVANCE;
        if (p.startsWith("/results")) return Decision.CHECK_RELEVANCE;

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

    /**
     * Strips "www." prefix only. Preserves meaningful subdomains like
     * "scholar.google.com" or "old.reddit.com".
     */
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