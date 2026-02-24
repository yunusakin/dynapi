package com.dynapi.infrastructure.messaging;

import com.dynapi.domain.event.DomainEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {
  private final KafkaTemplate<String, Object> kafkaTemplate;

  public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publishSchemaChange(DomainEvent<?> event) {
    kafkaTemplate.send(KafkaConfig.SCHEMA_CHANGES_TOPIC, event);
  }

  public void publishDataValidation(DomainEvent<?> event) {
    kafkaTemplate.send(KafkaConfig.DATA_VALIDATION_TOPIC, event);
  }

  public void publishAuditEvent(DomainEvent<?> event) {
    kafkaTemplate.send(KafkaConfig.AUDIT_EVENTS_TOPIC, event);
  }
}
