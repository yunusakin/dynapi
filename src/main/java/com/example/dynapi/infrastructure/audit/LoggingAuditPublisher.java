package com.example.dynapi.infrastructure.audit;

import com.example.dynapi.domain.event.AuditPublisher;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

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
