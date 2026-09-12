package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.AuditEntityType;
import com.dynapi.domain.model.AuditEntry;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.security.CurrentActorResolver;

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
    @Mock
    private CurrentActorResolver currentActorResolver;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        QueryGuardrailProperties guardrails = new QueryGuardrailProperties();
        guardrails.setMaxPageSize(100);
        auditService = new AuditService(mongoTemplate, guardrails, currentActorResolver);
    }

    @Test
    void record_savesEntryWithActorAndTimestamp() {
        when(currentActorResolver.resolve()).thenReturn("alice");
        Map<String, Object> before = Map.of("title", "Old");
        Map<String, Object> after = Map.of("title", "New");

        auditService.record(AuditEntityType.RECORD, "tasks", "abc123", "RECORD_PATCHED", before, after);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(mongoTemplate).save(captor.capture());
        AuditEntry saved = captor.getValue();

        assertEquals(AuditEntityType.RECORD, saved.getEntityType());
        assertEquals("tasks", saved.getEntityName());
        assertEquals("abc123", saved.getEntityId());
        assertEquals("RECORD_PATCHED", saved.getAction());
        assertEquals("alice", saved.getActor());
        assertEquals(before, saved.getBefore());
        assertEquals(after, saved.getAfter());
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getTimestamp());
    }

    @Test
    void record_swallowsDataAccessFailureInsteadOfPropagating() {
        when(currentActorResolver.resolve()).thenReturn("alice");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("mongo down"))
                .when(mongoTemplate)
                .save(any(AuditEntry.class));

        assertDoesNotThrow(
                () ->
                        auditService.record(
                                AuditEntityType.RECORD,
                                "tasks",
                                "abc123",
                                "RECORD_PATCHED",
                                Map.of(),
                                Map.of()));
    }

    @Test
    void query_appliesFiltersAndReturnsPaginatedResult() {
        AuditEntry entry = new AuditEntry();
        entry.setEntityType(AuditEntityType.SCHEMA);
        entry.setEntityName("tasks");
        entry.setAction("SCHEMA_PUBLISHED");

        when(mongoTemplate.count(any(Query.class), eq(AuditEntry.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(AuditEntry.class))).thenReturn(List.of(entry));

        PaginatedResponse<AuditEntry> result =
                auditService.query("SCHEMA", "tasks", null, "SCHEMA_PUBLISHED", 0, 10);

        assertEquals(0, result.page());
        assertEquals(10, result.size());
        assertEquals(1L, result.totalElements());
        assertEquals(1, result.content().size());
        assertEquals("SCHEMA_PUBLISHED", result.content().get(0).getAction());
    }

    @Test
    void query_withOnlyEntityTypeReturnsEntriesAcrossAllEntityNames() {
        AuditEntry recordOne = new AuditEntry();
        recordOne.setEntityType(AuditEntityType.RECORD);
        recordOne.setEntityName("tasks");

        AuditEntry recordTwo = new AuditEntry();
        recordTwo.setEntityType(AuditEntityType.RECORD);
        recordTwo.setEntityName("orders");

        when(mongoTemplate.count(any(Query.class), eq(AuditEntry.class))).thenReturn(2L);
        when(mongoTemplate.find(any(Query.class), eq(AuditEntry.class)))
                .thenReturn(List.of(recordOne, recordTwo));

        PaginatedResponse<AuditEntry> result = auditService.query("RECORD", null, null, null, 0, 10);

        assertEquals(2L, result.totalElements());
        assertEquals(2, result.content().size());
    }

    @Test
    void query_countsBeforePaginationIsAppliedToTheSharedQuery() {
        // Query is a single mutable instance reused for both count() and find(): inspecting it via
        // an ArgumentCaptor after query() returns would see the final, already-paginated state, so
        // the limit/skip at the moment of the count() call must be captured with an Answer instead.
        int[] limitAtCountTime = new int[1];
        long[] skipAtCountTime = new long[1];
        when(mongoTemplate.count(any(Query.class), eq(AuditEntry.class)))
                .thenAnswer(
                        invocation -> {
                            Query query = invocation.getArgument(0);
                            limitAtCountTime[0] = query.getLimit();
                            skipAtCountTime[0] = query.getSkip();
                            return 5L;
                        });
        when(mongoTemplate.find(any(Query.class), eq(AuditEntry.class))).thenReturn(List.of());

        auditService.query(null, null, null, null, 0, 2);

        assertEquals(0, limitAtCountTime[0]);
        assertEquals(0, skipAtCountTime[0]);
    }

    @Test
    void query_rejectsSizeAboveGuardrail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> auditService.query(null, null, null, null, 0, 500));
    }

    @Test
    void query_rejectsNegativePage() {
        assertThrows(
                IllegalArgumentException.class,
                () -> auditService.query(null, null, null, null, -1, 10));
    }

    @Test
    void query_rejectsUnknownEntityType() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> auditService.query("NOT_A_REAL_TYPE", null, null, null, 0, 10));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("Unknown entityType"));
    }

    @Test
    void query_entityTypeIsCaseInsensitive() {
        when(mongoTemplate.count(any(Query.class), eq(AuditEntry.class))).thenReturn(0L);
        when(mongoTemplate.find(any(Query.class), eq(AuditEntry.class))).thenReturn(List.of());

        assertDoesNotThrow(() -> auditService.query("record", null, null, null, 0, 10));
    }
}
