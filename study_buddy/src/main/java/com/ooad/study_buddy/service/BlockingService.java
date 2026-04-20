package com.ooad.study_buddy.service;

import com.ooad.study_buddy.model.SiteMetadata;
import com.ooad.study_buddy.repository.SiteMetadataRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.sql.*;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * SERVICE — Blocking rules & platform-specific logic
 *
 * ADDED: MySQL persistence for whitelist/blacklist add & remove operations.
 * When a domain is added or removed via whitelist()/blacklist()/removeDomain(),
 * the change is written to both the in-memory repository AND the MySQL database
 * so it survives application restarts.
 */
@Service
public class BlockingService {

    private static final Logger LOG = Logger.getLogger(BlockingService.class.getName());

    // ── MySQL connection settings (mirrors DatabaseSeedService) ───────────────
    private static final String DB_URL  =
            "jdbc:mysql://localhost:3306/studybuddy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123";

    private final SiteMetadataRepository ruleRepo;

    public BlockingService(SiteMetadataRepository ruleRepo) {
        this.ruleRepo = ruleRepo;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public enum Decision { ALLOW, BLOCK, CHECK_RELEVANCE }

    /**
     * Fast structural decision — called before any content extraction.
     */
    public Decision quickDecision(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return Decision.ALLOW;

        String domain = extractDomain(rawUrl);
        String path   = extractPath(rawUrl);

        if (isWhitelisted(domain)) {
            LOG.fine("[BLOCKING] ALLOW (whitelist): " + rawUrl);
            return Decision.ALLOW;
        }
        if (isBlacklisted(domain)) {
            LOG.info("[BLOCKING] BLOCK (blacklist): " + rawUrl);
            return Decision.BLOCK;
        }
        if (isKnownDistraction(domain)) {
            LOG.info("[BLOCKING] BLOCK (known distraction): " + rawUrl);
            return Decision.BLOCK;
        }
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

    // ── Whitelist / Blacklist management (in-memory + MySQL) ──────────────────

    /**
     * Adds a domain to the whitelist in both in-memory repo and MySQL.
     */
    public void whitelist(String domain, String notes) {
        String normalized = normalizeDomain(domain);
        upsert(normalized, SiteMetadata.RuleType.WHITELIST, notes);
        persistToMySQL(normalized, "WHITELIST", notes);
    }

    /**
     * Adds a domain to the blacklist in both in-memory repo and MySQL.
     */
    public void blacklist(String domain, String notes) {
        String normalized = normalizeDomain(domain);
        upsert(normalized, SiteMetadata.RuleType.BLACKLIST, notes);
        persistToMySQL(normalized, "BLACKLIST", notes);
    }

    /**
     * Removes a domain from both the in-memory repo and MySQL.
     */
    public void removeDomain(String domain) {
        String normalized = normalizeDomain(domain);
        ruleRepo.findByDomain(normalized).ifPresent(ruleRepo::delete);
        deleteFromMySQL(normalized);
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

    // ── MySQL persistence helpers ─────────────────────────────────────────────

    /**
     * Inserts or updates the rule in MySQL using INSERT ... ON DUPLICATE KEY UPDATE.
     */
    private void persistToMySQL(String domain, String ruleType, String notes) {
        String sql = "INSERT INTO blocking_rules (domain, rule_type, notes) VALUES (?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE rule_type = VALUES(rule_type), notes = VALUES(notes)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            ps.setString(2, ruleType);
            ps.setString(3, notes != null ? notes : "");
            ps.executeUpdate();
            LOG.info("[DB] Persisted " + ruleType + " rule for: " + domain);
        } catch (SQLException e) {
            LOG.warning("[DB] Failed to persist rule for " + domain + ": " + e.getMessage());
        }
    }

    /**
     * Deletes the rule from MySQL.
     */
    private void deleteFromMySQL(String domain) {
        String sql = "DELETE FROM blocking_rules WHERE domain = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, domain);
            int rows = ps.executeUpdate();
            LOG.info("[DB] Deleted rule for: " + domain + " (" + rows + " rows affected)");
        } catch (SQLException e) {
            LOG.warning("[DB] Failed to delete rule for " + domain + ": " + e.getMessage());
        }
    }

    // ── Platform rules ────────────────────────────────────────────────────────

    private Decision youtubeDecision(String path) {
        if (path == null || path.isBlank()) return Decision.CHECK_RELEVANCE;
        String p = (path.length() > 1 && path.endsWith("/"))
                ? path.substring(0, path.length() - 1) : path;
        if (p.startsWith("/shorts")) return Decision.BLOCK;
        if (p.startsWith("/watch"))   return Decision.CHECK_RELEVANCE;
        if (p.startsWith("/results")) return Decision.CHECK_RELEVANCE;
        return Decision.CHECK_RELEVANCE;
    }

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