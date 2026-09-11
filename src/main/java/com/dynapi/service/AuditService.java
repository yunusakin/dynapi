package com.dynapi.service;

import com.dynapi.config.PageRequestGuard;
import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.AuditEntry;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.security.CurrentActorResolver;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
     * Records an audit entry. Failures here are logged and swallowed rather than propagated: audit
     * logging is a side effect of an already-completed mutation, and a transient failure to record
     * it must never make an otherwise-successful operation appear to have failed to the caller.
     */
    public void record(
            String entityType,
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
            entry.setTimestamp(LocalDateTime.now());
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
        if (entityType != null && !entityType.isBlank()) {
            query.addCriteria(Criteria.where("entityType").is(entityType));
        }
        if (entityName != null && !entityName.isBlank()) {
            query.addCriteria(Criteria.where("entityName").is(entityName));
        }
        if (entityId != null && !entityId.isBlank()) {
            query.addCriteria(Criteria.where("entityId").is(entityId));
        }
        if (action != null && !action.isBlank()) {
            query.addCriteria(Criteria.where("action").is(action));
        }

        long total = mongoTemplate.count(query, AuditEntry.class);

        query.with(Sort.by(Sort.Direction.DESC, "timestamp"));
        query.with(PageRequest.of(resolvedPage, resolvedSize));

        var content = mongoTemplate.find(query, AuditEntry.class);
        return new PaginatedResponse<>(resolvedPage, resolvedSize, total, content, "timestamp", "DESC");
    }
}
