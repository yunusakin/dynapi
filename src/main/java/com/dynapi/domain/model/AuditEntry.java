package com.dynapi.domain.model;

import java.time.Instant;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "audit_log")
public class AuditEntry {
    @Id
    private String id;
    private AuditEntityType entityType;
    private String entityName;
    private String entityId;
    private String action;
    private String actor;
    private Instant timestamp;
    private Object before;
    private Object after;
}
