package com.dynapi.infrastructure.audit;

import com.dynapi.domain.event.AuditPublisher;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingAuditPublisher implements AuditPublisher {
    private static final Logger logger = LoggerFactory.getLogger(LoggingAuditPublisher.class);

    @Override
    public void publish(String action, String entity, Map<String, Object> data) {
        logger.info("Audit: {} on {} with data: {}", action, entity, data);
        // In a real application, you might want to:
        // 1. Store in audit database
        // 2. Send to audit message queue
        // 3. Call audit service
    }
}
