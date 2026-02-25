package com.dynapi.domain.event;

import java.time.LocalDateTime;
import java.util.Map;

import lombok.Data;

@Data
public class DomainEvent<T> {
    private String eventType;
    private String entityName;
    private String userId;
    private LocalDateTime timestamp;
    private T payload;
    private Map<String, String> metadata;
}
