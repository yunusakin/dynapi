package com.example.dynapi.audit;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import java.util.Map;

public class AuditEvent extends ApplicationEvent {
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
    public String getAction() { return action; }
    public String getEntity() { return entity; }
    public Map<String, Object> getDetails() { return details; }
    public Instant getTimestamp() { return timestamp; }
}

@Component
public class AuditPublisher {
    @Autowired
    private ApplicationEventPublisher publisher;
    public void publish(String action, String entity, Map<String, Object> details) {
        publisher.publishEvent(new AuditEvent(this, action, entity, details));
    }
}
