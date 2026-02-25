package com.dynapi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "dynapi.query.guardrails")
public class QueryGuardrailProperties {
    private int maxPageSize = 100;
    private int maxFilterDepth = 3;
    private int maxRuleCount = 20;
}
