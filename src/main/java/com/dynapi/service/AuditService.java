package com.dynapi.service;

import com.dynapi.config.AsyncAuditConfig;
import com.dynapi.config.PageRequestGuard;
import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.AuditEntityType;
import com.dynapi.domain.model.AuditEntry;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.security.CurrentActorResolver;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
     * Records an audit entry off the request thread. Failures here are logged and swallowed rather
     * than propagated: audit logging is a side effect of an already-completed mutation, and a
     * transient failure to record it must never make an otherwise-successful operation appear to
     * have failed to the caller. Only DataAccessException (Spring's infra-failure hierarchy) is
     * swallowed here; a genuine programming bug elsewhere in this method still surfaces.
     */
    @Async(AsyncAuditConfig.AUDIT_EXECUTOR)
    public void record(
            AuditEntityType entityType,
            String entityName,
            String entityId,
            String action,
            Object before,
            Object after) {
        AuditEntry entry = new AuditEntry();
        entry.setEntityType(entityType);
        entry.setEntityName(entityName);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setActor(currentActorResolver.resolve());
        entry.setTimestamp(LocalDateTime.now(Clock.systemUTC()));
        entry.setBefore(before);
        entry.setAfter(after);
        try {
            mongoTemplate.save(entry);
        } catch (DataAccessException ex) {
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
