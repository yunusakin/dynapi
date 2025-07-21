package com.dynapi.infrastructure.messaging;

import com.dynapi.domain.event.DomainEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {
    private final RabbitTemplate rabbitTemplate;
    
    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }
    
    public void publishSchemaChange(DomainEvent<?> event) {
        rabbitTemplate.convertAndSend("schema-changes", event);
    }
    
    public void publishDataValidation(DomainEvent<?> event) {
        rabbitTemplate.convertAndSend("data-validation", event);
    }
    
    public void publishAuditEvent(DomainEvent<?> event) {
        rabbitTemplate.convertAndSend("audit-events", event);
    }
}
