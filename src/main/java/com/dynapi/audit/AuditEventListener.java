package com.dynapi.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditEventListener {
    private static final Logger logger = LoggerFactory.getLogger(AuditEventListener.class);

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @EventListener
    public void handleAuditEvent(AuditEvent event) {
        logger.info("AUDIT | action={} | entity={} | details={} | timestamp={}",
                event.getAction(), event.getEntity(), event.getDetails(), event.getInstantTimestamp());
        // Persist audit logs to MongoDB
        mongoTemplate.save(new AuditLog(event.getAction(), event.getEntity(), event.getDetails(), event.getInstantTimestamp()), "audit_logs");
    }

    // Simple audit log document
    public static class AuditLog {
        private String action;
        private String entity;
        private Object details;
        private java.time.Instant timestamp;
        public AuditLog(String action, String entity, Object details, java.time.Instant timestamp) {
            this.action = action;
            this.entity = entity;
            this.details = details;
            this.timestamp = timestamp;
        }
        // getters and setters omitted for brevity
    }
}
