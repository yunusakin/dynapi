package com.dynapi.domain.event;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class DomainEvent<T> {
    private String eventType;
    private String entityName;
    private String userId;
    private LocalDateTime timestamp;
    private T payload;
    private Map<String, String> metadata;
}
