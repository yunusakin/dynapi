package com.dynapi.audit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AuditPublisher {
    @Autowired
    private ApplicationEventPublisher publisher;
    public void publish(String action, String entity, Map<String, Object> details) {
        publisher.publishEvent(new AuditEvent(this, action, entity, details));
    }
}
