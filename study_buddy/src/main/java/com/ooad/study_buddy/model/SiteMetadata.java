package com.ooad.study_buddy.model;

import jakarta.persistence.*;

/**
 * JPA entity backing the blocking_rules table.
 * SRP: Only represents one stored rule row.
 */
@Entity
@Table(name = "blocking_rules")
public class SiteMetadata {

    public enum RuleType { WHITELIST, BLACKLIST }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType ruleType;

    @Column
    private String notes;

    public SiteMetadata() {}

    public SiteMetadata(String domain, RuleType ruleType, String notes) {
        this.domain   = domain;
        this.ruleType = ruleType;
        this.notes    = notes;
    }

    public Long     getId()       { return id; }
    public String   getDomain()   { return domain; }
    public RuleType getRuleType() { return ruleType; }
    public String   getNotes()    { return notes; }
}
