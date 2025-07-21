package com.dynapi.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AuditPublisher {
    private final ApplicationEventPublisher publisher;

    public void publish(String action, String entity, Map<String, Object> details) {
        publisher.publishEvent(new AuditEvent(this, action, entity, details));
    }
}
