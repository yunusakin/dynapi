package com.dynapi.service;

import com.dynapi.config.AsyncAuditConfig;
import com.dynapi.config.PageRequestGuard;
import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.AuditEntityType;
import com.dynapi.domain.model.AuditEntry;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.security.CurrentActorResolver;

import java.time.Instant;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final MongoTemplate mongoTemplate;
    private final QueryGuardrailProperties guardrailProperties;
    private final CurrentActorResolver currentActorResolver;

    /**
     * Records an audit entry off the request thread. The entire body is guarded: audit logging is a
     * side effect of an already-completed mutation, and neither a Mongo failure nor a bug in this
     * method's own entry-building logic may ever surface to the caller as a failed mutation. Callers
     * MUST invoke this through the injected AuditService bean (never self-invoked from within
     * AuditService), or Spring's @Async proxy will not intercept the call and it will run
     * synchronously.
     */
    @Async(AsyncAuditConfig.AUDIT_EXECUTOR)
    public void record(
            AuditEntityType entityType,
            String entityName,
            String entityId,
            String action,
            Object before,
            Object after) {
        try {
            AuditEntry entry = new AuditEntry();
            entry.setEntityType(entityType);
            entry.setEntityName(entityName);
            entry.setEntityId(entityId);
            entry.setAction(action);
            entry.setActor(currentActorResolver.resolve());
            entry.setTimestamp(Instant.now());
            entry.setBefore(before);
            entry.setAfter(after);
            mongoTemplate.save(entry);
        } catch (RuntimeException ex) {
            log.error(
                    "Failed to record audit entry for entityType={}, entityName={}, entityId={}, action={}",
                    entityType,
                    entityName,
                    entityId,
                    action,
                    ex);
        }
    }

    public PaginatedResponse<AuditEntry> query(
            String entityType,
            String entityName,
            String entityId,
            String action,
            Integer page,
            Integer size) {
        int resolvedPage = PageRequestGuard.resolvePage(page, DEFAULT_PAGE);
        int resolvedSize = PageRequestGuard.resolveSize(size, DEFAULT_SIZE, guardrailProperties);

        Query query = new Query();
        addEqualsIfPresent(query, "entityType", parseEntityType(entityType));
        addEqualsIfPresent(query, "entityName", entityName);
        addEqualsIfPresent(query, "entityId", entityId);
        addEqualsIfPresent(query, "action", action);

        // Count before applying skip/limit: Query is mutable and count()/find() share this instance,
        // so counting after pagination is applied would cap `total` at resolvedSize.
        long total = mongoTemplate.count(query, AuditEntry.class);

        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        query.with(PageRequest.of(resolvedPage, resolvedSize));

        var content = mongoTemplate.find(query, AuditEntry.class);
        return new PaginatedResponse<>(resolvedPage, resolvedSize, total, content, "timestamp", "DESC");
    }

    private AuditEntityType parseEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            return null;
        }
        try {
            return AuditEntityType.valueOf(entityType.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown entityType '"
                            + entityType
                            + "'. Allowed values: "
                            + Arrays.toString(AuditEntityType.values()));
        }
    }

    private void addEqualsIfPresent(Query query, String field, Object value) {
        boolean present = value instanceof String stringValue ? !stringValue.isBlank() : value != null;
        if (present) {
            query.addCriteria(Criteria.where(field).is(value));
        }
    }
}
