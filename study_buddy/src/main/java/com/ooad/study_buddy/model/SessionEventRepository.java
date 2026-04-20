package com.ooad.study_buddy.repository;

import com.ooad.study_buddy.model.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for session_events table.
 * Follows same pattern as SiteMetadataRepository.
 */
@Repository
public interface SessionEventRepository
        extends JpaRepository<SessionEvent, Long> {

    List<SessionEvent> findBySessionIdOrderByOccurredAtAsc(Long sessionId);
}