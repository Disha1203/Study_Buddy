package com.ooad.study_buddy.repository;

import com.ooad.study_buddy.model.SiteMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository layer — DIP: upper layers depend on this interface, not H2/SQL.
 */
@Repository
public interface SiteMetadataRepository extends JpaRepository<SiteMetadata, Long> {

    Optional<SiteMetadata> findByDomain(String domain);

    boolean existsByDomainAndRuleType(String domain, SiteMetadata.RuleType ruleType);
}
