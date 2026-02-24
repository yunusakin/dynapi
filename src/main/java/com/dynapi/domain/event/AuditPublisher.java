package com.dynapi.domain.event;

import java.util.Map;

public interface AuditPublisher {
  void publish(String action, String entity, Map<String, Object> data);
}
