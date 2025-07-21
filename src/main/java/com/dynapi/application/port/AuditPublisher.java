package com.dynapi.application.port;

public interface AuditPublisher {
    void publish(String eventType, String entity, Object data);
}
