package com.dynapi.domain.service.impl;

import com.dynapi.domain.service.SchemaVersionService;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.event.DomainEvent;
import com.dynapi.infrastructure.messaging.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class SchemaVersionServiceImpl implements SchemaVersionService {
    private final MongoTemplate mongoTemplate;
    private final EventPublisher eventPublisher;

    public SchemaVersionServiceImpl(MongoTemplate mongoTemplate, EventPublisher eventPublisher) {
        this.mongoTemplate = mongoTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public SchemaVersion createNewVersion(String entityName, List<FieldDefinition> fields) {
        // Get current active version
        SchemaVersion currentVersion = getActiveVersion(entityName);
        int newVersion = currentVersion != null ? currentVersion.getVersion() + 1 : 1;

        // Create new version
        SchemaVersion schemaVersion = new SchemaVersion();
        schemaVersion.setEntityName(entityName);
        schemaVersion.setVersion(newVersion);
        schemaVersion.setFields(new ArrayList<>(fields));
        schemaVersion.setStatus("DRAFT");
        schemaVersion.setCreatedAt(LocalDateTime.now());
        
        SchemaVersion saved = mongoTemplate.save(schemaVersion);

        // Publish event
        DomainEvent<SchemaVersion> event = new DomainEvent<>();
        event.setEventType("SCHEMA_VERSION_CREATED");
        event.setEntityName(entityName);
        event.setTimestamp(LocalDateTime.now());
        event.setPayload(saved);
        eventPublisher.publishSchemaChange(event);

        return saved;
    }

    @Override
    public SchemaVersion getActiveVersion(String entityName) {
        Query query = new Query(Criteria.where("entityName").is(entityName)
                .and("status").is("ACTIVE"));
        return mongoTemplate.findOne(query, SchemaVersion.class);
    }

    @Override
    public SchemaVersion getVersion(String entityName, Integer version) {
        Query query = new Query(Criteria.where("entityName").is(entityName)
                .and("version").is(version));
        return mongoTemplate.findOne(query, SchemaVersion.class);
    }

    @Override
    public void activateVersion(String entityName, Integer version) {
        // Deactivate current active version
        Query activeQuery = new Query(Criteria.where("entityName").is(entityName)
                .and("status").is("ACTIVE"));
        Update deactivateUpdate = new Update().set("status", "DEPRECATED")
                .set("effectiveTo", LocalDateTime.now());
        mongoTemplate.updateFirst(activeQuery, deactivateUpdate, SchemaVersion.class);

        // Activate new version
        Query newVersionQuery = new Query(Criteria.where("entityName").is(entityName)
                .and("version").is(version));
        Update activateUpdate = new Update().set("status", "ACTIVE")
                .set("effectiveFrom", LocalDateTime.now());
        mongoTemplate.updateFirst(newVersionQuery, activateUpdate, SchemaVersion.class);

        // Publish event
        DomainEvent<Map<String, Object>> event = new DomainEvent<>();
        event.setEventType("SCHEMA_VERSION_ACTIVATED");
        event.setEntityName(entityName);
        event.setTimestamp(LocalDateTime.now());
        event.setPayload(Map.of("version", version));
        eventPublisher.publishSchemaChange(event);
    }

    @Override
    public void deprecateVersion(String entityName, Integer version) {
        Query query = new Query(Criteria.where("entityName").is(entityName)
                .and("version").is(version));
        Update update = new Update().set("status", "DEPRECATED")
                .set("effectiveTo", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, SchemaVersion.class);
    }

    @Override
    public Map<String, Object> migrateData(Map<String, Object> data, Integer fromVersion, Integer toVersion) {
        // This is a simplified migration that just copies the data
        // In a real implementation, you would:
        // 1. Compare field definitions between versions
        // 2. Apply transformation rules
        // 3. Handle removed fields
        // 4. Handle new required fields
        // 5. Validate the migrated data
        return new HashMap<>(data);
    }
}
