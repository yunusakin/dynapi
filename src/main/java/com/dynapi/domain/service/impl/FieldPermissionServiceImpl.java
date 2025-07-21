package com.dynapi.domain.service.impl;

import com.dynapi.domain.service.FieldPermissionService;
import com.dynapi.domain.model.FieldPermission;
import com.dynapi.domain.event.DomainEvent;
import com.dynapi.infrastructure.messaging.EventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FieldPermissionServiceImpl implements FieldPermissionService {
    private final MongoTemplate mongoTemplate;
    private final EventPublisher eventPublisher;

    public FieldPermissionServiceImpl(MongoTemplate mongoTemplate, EventPublisher eventPublisher) {
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public boolean hasReadPermission(String fieldName, String entityName, Set<String> userRoles) {
        FieldPermission permission = getFieldPermission(fieldName, entityName);
        return permission == null || 
               permission.getReadRoles() == null || 
               permission.getReadRoles().isEmpty() ||
               !Collections.disjoint(permission.getReadRoles(), userRoles);
    }

    @Override
    public boolean hasWritePermission(String fieldName, String entityName, Set<String> userRoles) {
        FieldPermission permission = getFieldPermission(fieldName, entityName);
        return permission == null || 
               permission.getWriteRoles() == null || 
               permission.getWriteRoles().isEmpty() ||
               !Collections.disjoint(permission.getWriteRoles(), userRoles);
    }

    @Override
    public Map<String, Object> applyFieldMasking(Map<String, Object> data, String entityName, Set<String> userRoles) {
        Map<String, Object> maskedData = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String fieldName = entry.getKey();
            Object value = entry.getValue();

            if (!hasReadPermission(fieldName, entityName, userRoles)) {
                continue; // Skip fields user can't read
            }

            FieldPermission permission = getFieldPermission(fieldName, entityName);
            if (permission != null && permission.isMasked() && !userRoles.contains("ADMIN")) {
                value = maskValue(value, permission.getMaskPattern());
            }

            maskedData.put(fieldName, value);
        }

        return maskedData;
    }

    @Override
    public FieldPermission setFieldPermissions(String fieldName, String entityName, Set<String> readRoles, Set<String> writeRoles) {
        FieldPermission permission = getFieldPermission(fieldName, entityName);
        if (permission == null) {
            permission = new FieldPermission();
            permission.setFieldName(fieldName);
            permission.setEntityName(entityName);
        }
        
        permission.setReadRoles(readRoles);
        permission.setWriteRoles(writeRoles);
        
        FieldPermission saved = mongoTemplate.save(permission);

        // Publish event
        DomainEvent<FieldPermission> event = new DomainEvent<>();
        event.setEventType("FIELD_PERMISSION_UPDATED");
        event.setEntityName(entityName);
        event.setTimestamp(LocalDateTime.now());
        event.setPayload(saved);
        eventPublisher.publishAuditEvent(event);

        return saved;
    }

    @Override
    public void removeFieldPermissions(String fieldName, String entityName) {
        Query query = new Query(Criteria.where("fieldName").is(fieldName)
                .and("entityName").is(entityName));
        mongoTemplate.remove(query, FieldPermission.class);
    }

    private FieldPermission getFieldPermission(String fieldName, String entityName) {
        Query query = new Query(Criteria.where("fieldName").is(fieldName)
                .and("entityName").is(entityName));
        return mongoTemplate.findOne(query, FieldPermission.class);
    }

    private Object maskValue(Object value, String pattern) {
        if (value == null) return null;
        if (pattern != null && !pattern.isEmpty()) {
            return pattern.replace('X', '*');
        }
        return "*****"; // Default mask
    }
}
