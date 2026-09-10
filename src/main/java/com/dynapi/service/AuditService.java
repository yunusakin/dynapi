package com.dynapi.service;

import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.AuditEntry;
import com.dynapi.dto.PaginatedResponse;

import java.time.LocalDateTime;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    private final MongoTemplate mongoTemplate;
    private final QueryGuardrailProperties guardrailProperties;

    public void record(String entityType, String entityId, String action, Object before, Object after) {
        AuditEntry entry = new AuditEntry();
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setAction(action);
        entry.setActor(currentActor());
        entry.setTimestamp(LocalDateTime.now());
        entry.setBefore(before);
        entry.setAfter(after);
        mongoTemplate.save(entry);
    }

    public PaginatedResponse<AuditEntry> query(
            String entityType, String entityId, String action, Integer page, Integer size) {
        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size);

        Query query = new Query();
        if (entityType != null && !entityType.isBlank()) {
            query.addCriteria(Criteria.where("entityType").is(entityType));
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

    private int resolvePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page must be >= 0");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be > 0");
        }
        if (size > guardrailProperties.getMaxPageSize()) {
            throw new IllegalArgumentException(
                    "Size exceeds max page size: " + guardrailProperties.getMaxPageSize());
        }
        return size;
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }
}
