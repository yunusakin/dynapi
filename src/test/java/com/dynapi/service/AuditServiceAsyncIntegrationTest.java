package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.dynapi.config.AsyncAuditConfig;
import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.AuditEntityType;
import com.dynapi.domain.model.AuditEntry;
import com.dynapi.security.CurrentActorResolver;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Exercises AuditService.record() through a REAL Spring @Async proxy (unlike every other test in
 * this suite, which constructs AuditService directly with `new AuditService(...)`, bypassing
 * Spring AOP entirely and making @Async inert). This is the only test that would catch a
 * regression like: removing the @Async qualifier, renaming the executor bean, or losing
 * SecurityContext propagation across the async boundary (which happened once already during
 * development of this feature and was caught only by manual live verification).
 */
@ExtendWith(SpringExtension.class)
@SpringJUnitConfig(classes = {AsyncAuditConfig.class, AuditServiceAsyncIntegrationTest.TestConfig.class})
class AuditServiceAsyncIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void record_runsAsynchronouslyAndPropagatesTheRealActor() {
        String callingThreadName = Thread.currentThread().getName();
        String[] executingThreadName = new String[1];
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            executingThreadName[0] = Thread.currentThread().getName();
                            return null;
                        })
                .when(mongoTemplate)
                .save(org.mockito.ArgumentMatchers.any(AuditEntry.class));

        Authentication authentication =
                new UsernamePasswordAuthenticationToken("alice", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        auditService.record(AuditEntityType.RECORD, "tasks", "id-1", "RECORD_PATCHED", null, null);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mongoTemplate, timeout(2000)).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertEquals("alice", saved.getActor(), "actor must survive the async boundary");
        assertNotEquals(
                callingThreadName, executingThreadName[0], "record() must not run on the caller's thread");
        assertTrue(
                executingThreadName[0].startsWith("audit-"),
                "expected the dedicated audit executor's thread, got: " + executingThreadName[0]);
    }

    static class TestConfig {
        @Bean
        MongoTemplate mongoTemplate() {
            return mock(MongoTemplate.class);
        }

        @Bean
        QueryGuardrailProperties queryGuardrailProperties() {
            QueryGuardrailProperties properties = new QueryGuardrailProperties();
            properties.setMaxPageSize(100);
            return properties;
        }

        @Bean
        CurrentActorResolver currentActorResolver() {
            return new CurrentActorResolver();
        }

        @Bean
        AuditService auditService(
                MongoTemplate mongoTemplate,
                QueryGuardrailProperties queryGuardrailProperties,
                CurrentActorResolver currentActorResolver) {
            return new AuditService(mongoTemplate, queryGuardrailProperties, currentActorResolver);
        }
    }
}
