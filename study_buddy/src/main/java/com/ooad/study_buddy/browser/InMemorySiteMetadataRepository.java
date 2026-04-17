package com.ooad.study_buddy.browser;

import com.ooad.study_buddy.model.SiteMetadata;
import com.ooad.study_buddy.repository.SiteMetadataRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * Lightweight in-memory implementation of SiteMetadataRepository.
 *
 * Used when the JavaFX app starts outside the Spring ApplicationContext
 * (i.e. via BrowserLauncher.main). In production, replace this with the
 * real Spring Data JPA bean wired by @SpringBootApplication.
 *
 * All methods not needed by BlockingService throw UnsupportedOperationException
 * to keep the footprint minimal.
 */
public class InMemorySiteMetadataRepository implements SiteMetadataRepository {

    private final Map<Long, SiteMetadata>  store   = new ConcurrentHashMap<>();
    private final Map<String, Long>        byDomain = new ConcurrentHashMap<>();
    private final AtomicLong               idGen   = new AtomicLong(1);

    @Override
    public Optional<SiteMetadata> findByDomain(String domain) {
        Long id = byDomain.get(domain);
        if (id == null) return Optional.empty();
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public boolean existsByDomainAndRuleType(String domain, SiteMetadata.RuleType ruleType) {
        return findByDomain(domain)
                .map(m -> m.getRuleType() == ruleType)
                .orElse(false);
    }

    @Override
    public <S extends SiteMetadata> S save(S entity) {
        // Use reflection-free approach: read id via getDeclaredField workaround
        // For simplicity we assign a new id if entity has no id
        try {
            var idField = SiteMetadata.class.getDeclaredField("id");
            idField.setAccessible(true);
            Long existingId = (Long) idField.get(entity);
            if (existingId == null) {
                long newId = idGen.getAndIncrement();
                idField.set(entity, newId);
                store.put(newId, entity);
                byDomain.put(entity.getDomain(), newId);
            } else {
                store.put(existingId, entity);
                byDomain.put(entity.getDomain(), existingId);
            }
        } catch (Exception e) {
            throw new RuntimeException("InMemoryRepo save failed", e);
        }
        return entity;
    }

    @Override public List<SiteMetadata> findAll() { return new ArrayList<>(store.values()); }
    @Override public Optional<SiteMetadata> findById(Long id) { return Optional.ofNullable(store.get(id)); }
    @Override public boolean existsById(Long id) { return store.containsKey(id); }
    @Override public long count() { return store.size(); }
    @Override public void deleteById(Long id) { SiteMetadata m = store.remove(id); if (m!=null) byDomain.remove(m.getDomain()); }
    @Override public void delete(SiteMetadata entity) { deleteById(entity.getId()); }
    @Override public void deleteAll() { store.clear(); byDomain.clear(); }

    // ── Stub implementations for unused JpaRepository methods ────────────────

    @Override public <S extends SiteMetadata> List<S> saveAll(Iterable<S> entities) { throw new UnsupportedOperationException(); }
    @Override public List<SiteMetadata> findAllById(Iterable<Long> longs) { throw new UnsupportedOperationException(); }
    @Override public void deleteAllById(Iterable<? extends Long> longs) { throw new UnsupportedOperationException(); }
    @Override public void deleteAll(Iterable<? extends SiteMetadata> entities) { throw new UnsupportedOperationException(); }
    @Override public List<SiteMetadata> findAll(Sort sort) { throw new UnsupportedOperationException(); }
    @Override public Page<SiteMetadata> findAll(Pageable pageable) { throw new UnsupportedOperationException(); }
    @Override public void flush() {}
    @Override public <S extends SiteMetadata> S saveAndFlush(S entity) { return save(entity); }
    @Override public <S extends SiteMetadata> List<S> saveAllAndFlush(Iterable<S> entities) { throw new UnsupportedOperationException(); }
    @Override public void deleteAllInBatch(Iterable<SiteMetadata> entities) { throw new UnsupportedOperationException(); }
    @Override public void deleteAllByIdInBatch(Iterable<Long> longs) { throw new UnsupportedOperationException(); }
    @Override public void deleteAllInBatch() { deleteAll(); }
    @Override public SiteMetadata getOne(Long aLong) { throw new UnsupportedOperationException(); }
    @Override public SiteMetadata getById(Long aLong) { return store.get(aLong); }
    @Override public SiteMetadata getReferenceById(Long aLong) { return store.get(aLong); }
    @Override public <S extends SiteMetadata> Optional<S> findOne(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends SiteMetadata> List<S> findAll(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends SiteMetadata> List<S> findAll(Example<S> example, Sort sort) { throw new UnsupportedOperationException(); }
    @Override public <S extends SiteMetadata> Page<S> findAll(Example<S> example, Pageable pageable) { throw new UnsupportedOperationException(); }
    @Override public <S extends SiteMetadata> long count(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends SiteMetadata> boolean exists(Example<S> example) { throw new UnsupportedOperationException(); }
    @Override public <S extends SiteMetadata, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
}
