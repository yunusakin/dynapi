package com.dynapi.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  public static final String SCHEMA_CHANGES_TOPIC = "schema-changes";
  public static final String DATA_VALIDATION_TOPIC = "data-validation";
  public static final String AUDIT_EVENTS_TOPIC = "audit-events";

  @Bean
  public NewTopic schemaChangesTopic() {
    return TopicBuilder.name(SCHEMA_CHANGES_TOPIC).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic dataValidationTopic() {
    return TopicBuilder.name(DATA_VALIDATION_TOPIC).partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic auditEventsTopic() {
    return TopicBuilder.name(AUDIT_EVENTS_TOPIC).partitions(1).replicas(1).build();
  }
}
