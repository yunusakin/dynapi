package com.dynapi.audit;

import java.time.Instant;
import java.util.Map;
import lombok.Getter;

@Getter
public class AuditEvent extends org.springframework.context.ApplicationEvent {
  private final String action;
  private final String entity;
  private final Map<String, Object> details;
  private final Instant instantTimestamp;

  public AuditEvent(Object source, String action, String entity, Map<String, Object> details) {
    super(source);
    this.action = action;
    this.entity = entity;
    this.details = details;
    this.instantTimestamp = Instant.now();
  }
}
