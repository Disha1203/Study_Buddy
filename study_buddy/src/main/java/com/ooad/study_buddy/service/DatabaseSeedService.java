package com.ooad.study_buddy.service;

import com.ooad.study_buddy.browser.InMemorySiteMetadataRepository;
import com.ooad.study_buddy.model.SiteMetadata;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.logging.Logger;

/**
 * SERVICE — Database Seed
 *
 * SRP: Only responsible for reading blocking_rules from MySQL
 *      and loading them into the in-memory repository.
 *
 * This bridges the gap between Spring JPA (MySQL) and the
 * manually constructed JavaFX service graph.
 */
@Service
public class DatabaseSeedService {

    private static final Logger LOG =
            Logger.getLogger(DatabaseSeedService.class.getName());

    // ── MySQL connection settings ─────────────────────────────────────────────
    private static final String DB_URL  =
            "jdbc:mysql://localhost:3306/studybuddy?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123"; // ← change this

    private final InMemorySiteMetadataRepository inMemoryRepo;

    public DatabaseSeedService(InMemorySiteMetadataRepository inMemoryRepo) {
        this.inMemoryRepo = inMemoryRepo;
    }

    /**
     * Reads all rows from blocking_rules in MySQL and
     * loads them into the in-memory repository.
     */
    public void loadFromDatabase() {
        String sql = "SELECT domain, rule_type, notes FROM blocking_rules";

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
             Statement  stmt = conn.createStatement();
             ResultSet  rs   = stmt.executeQuery(sql)) {

            int count = 0;
            while (rs.next()) {
                String domain   = rs.getString("domain");
                String ruleType = rs.getString("rule_type");
                String notes    = rs.getString("notes");

                SiteMetadata.RuleType type =
                        SiteMetadata.RuleType.valueOf(ruleType);

                inMemoryRepo.save(new SiteMetadata(domain, type, notes));
                count++;
            }

            LOG.info("[DB-SEED] Loaded " + count
                    + " blocking rules from MySQL into memory.");

        } catch (SQLException e) {
            LOG.severe("[DB-SEED] Failed to connect to MySQL: " + e.getMessage()
                    + " — falling back to empty ruleset.");
        }
    }
}