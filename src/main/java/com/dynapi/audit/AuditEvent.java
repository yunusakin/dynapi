package com.dynapi.audit;

import java.time.Instant;
import java.util.Map;

public class AuditEvent extends org.springframework.context.ApplicationEvent {
    private final String action;
    private final String entity;
    private final Map<String, Object> details;
    private final Instant timestamp;

    public AuditEvent(Object source, String action, String entity, Map<String, Object> details) {
        super(source);
        this.action = action;
        this.entity = entity;
        this.details = details;
        this.timestamp = Instant.now();
    }

    public String getAction() {
        return action;
    }

    public String getEntity() {
        return entity;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getInstantTimestamp() {
        return timestamp;
    }

}
