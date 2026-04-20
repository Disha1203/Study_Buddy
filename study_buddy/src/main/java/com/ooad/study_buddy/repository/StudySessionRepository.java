package com.ooad.study_buddy.repository;

import com.ooad.study_buddy.model.StudySession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for study_sessions table.
 * Follows same pattern as SiteMetadataRepository.
 */
@Repository
public interface StudySessionRepository
        extends JpaRepository<StudySession, Long> {
}