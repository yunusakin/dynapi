package com.dynapi.audit;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditPublisher {
    private final ApplicationEventPublisher publisher;

    public void publish(String action, String entity, Map<String, Object> details) {
        publisher.publishEvent(new AuditEvent(this, action, entity, details));
    }
}
