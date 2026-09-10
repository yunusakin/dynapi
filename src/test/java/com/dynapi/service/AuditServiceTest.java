package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.AuditEntry;
import com.dynapi.dto.PaginatedResponse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        QueryGuardrailProperties guardrails = new QueryGuardrailProperties();
        guardrails.setMaxPageSize(100);
        auditService = new AuditService(mongoTemplate, guardrails);
    }

    @Test
    void record_savesEntryWithActorAndTimestamp() {
        Map<String, Object> before = Map.of("title", "Old");
        Map<String, Object> after = Map.of("title", "New");

        auditService.record("RECORD:tasks", "abc123", "RECORD_PATCHED", before, after);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mongoTemplate).save(captor.capture());
        AuditEntry saved = captor.getValue();

        assertEquals("RECORD:tasks", saved.getEntityType());
        assertEquals("abc123", saved.getEntityId());
        assertEquals("RECORD_PATCHED", saved.getAction());
        assertEquals("system", saved.getActor());
        assertEquals(before, saved.getBefore());
        assertEquals(after, saved.getAfter());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getTimestamp());
    }

    @Test
    void query_appliesFiltersAndReturnsPaginatedResult() {
        AuditEntry entry = new AuditEntry();
        entry.setEntityType("SCHEMA");
        entry.setEntityId("tasks");
        entry.setAction("SCHEMA_PUBLISHED");

        when(mongoTemplate.count(any(Query.class), eq(AuditEntry.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(AuditEntry.class))).thenReturn(List.of(entry));

        PaginatedResponse<AuditEntry> result =
                auditService.query("SCHEMA", "tasks", "SCHEMA_PUBLISHED", 0, 10);

        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals(1, result.content().size());
        assertEquals("SCHEMA_PUBLISHED", result.content().get(0).getAction());
    }

    @Test
    void query_rejectsSizeAboveGuardrail() {
        assertThrows(
                IllegalArgumentException.class, () -> auditService.query(null, null, null, 0, 500));
    }

    @Test
    void query_rejectsNegativePage() {
        assertThrows(
                IllegalArgumentException.class, () -> auditService.query(null, null, null, -1, 10));
    }
}
