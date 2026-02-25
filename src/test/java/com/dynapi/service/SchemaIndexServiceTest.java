package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.dto.SchemaIndexSyncResult;

import java.time.LocalDateTime;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ExtendWith(MockitoExtension.class)
class SchemaIndexServiceTest {

    @Mock
    private SchemaLifecycleService schemaLifecycleService;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private IndexOperations indexOperations;

    private SchemaIndexService schemaIndexService;

    @BeforeEach
    void setUp() {
        schemaIndexService = new SchemaIndexService(schemaLifecycleService, mongoTemplate);
    }

    @Test
    void syncIndexes_ensuresUniqueAndIndexedFieldsFromPublishedSchema() {
        when(schemaLifecycleService.latestPublished("users")).thenReturn(publishedSchemaWithIndexes());
        when(mongoTemplate.indexOps("users")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo()).thenReturn(List.of());
        when(indexOperations.ensureIndex(any(IndexDefinition.class))).thenReturn("ok");

        SchemaIndexSyncResult result = schemaIndexService.syncIndexes("users");

        ArgumentCaptor<IndexDefinition> captor = ArgumentCaptor.forClass(IndexDefinition.class);
        verify(indexOperations, times(3)).ensureIndex(captor.capture());

        boolean emailUniqueSeen = false;
        boolean priorityIndexSeen = false;
        boolean cityIndexSeen = false;

        for (IndexDefinition definition : captor.getAllValues()) {
            Document keys = definition.getIndexKeys();
            String key = keys.keySet().iterator().next();
            Document options = definition.getIndexOptions();

            if ("email".equals(key)) {
                emailUniqueSeen = true;
                assertEquals(1, keys.get("email"));
                assertEquals(Boolean.TRUE, options.get("unique"));
            }
            if ("priority".equals(key)) {
                priorityIndexSeen = true;
            }
            if ("profile.city".equals(key)) {
                cityIndexSeen = true;
            }

            assertTrue(options.containsKey("partialFilterExpression"));
        }

        assertTrue(emailUniqueSeen);
        assertTrue(priorityIndexSeen);
        assertTrue(cityIndexSeen);

        assertEquals("users", result.entity());
        assertEquals(2, result.schemaVersion());
        assertEquals(3, result.requestedIndexes());
        assertEquals(3, result.ensuredIndexes());
        assertEquals(List.of("email"), result.uniqueFields());
        assertEquals(List.of("priority", "profile.city"), result.indexedFields());
    }

    @Test
    void syncIndexes_throwsOnConflictWhenUniqueRequestedButExistingIndexIsNonUnique() {
        when(schemaLifecycleService.latestPublished("users"))
                .thenReturn(publishedSchemaWithUniqueEmailOnly());
        when(mongoTemplate.indexOps("users")).thenReturn(indexOperations);
        when(indexOperations.getIndexInfo())
                .thenReturn(
                        List.of(
                                new IndexInfo(
                                        List.of(IndexField.create("email", Sort.Direction.ASC)),
                                        "email_1",
                                        false,
                                        false,
                                        "en")));

        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> schemaIndexService.syncIndexes("users"));

        assertTrue(ex.getMessage().contains("existing index is non-unique"));
        verify(indexOperations, never()).ensureIndex(any(IndexDefinition.class));
    }

    @Test
    void syncIndexes_returnsEmptyWhenSchemaHasNoIndexedFields() {
        when(schemaLifecycleService.latestPublished("users"))
                .thenReturn(publishedSchemaWithoutIndexFlags());

        SchemaIndexSyncResult result = schemaIndexService.syncIndexes("users");

        assertEquals(0, result.requestedIndexes());
        assertEquals(0, result.ensuredIndexes());
        verify(mongoTemplate, never()).indexOps(eq("users"));
    }

    private SchemaVersion publishedSchemaWithIndexes() {
        FieldDefinition email = new FieldDefinition();
        email.setFieldName("email");
        email.setType(FieldType.STRING);
        email.setUnique(true);

        FieldDefinition priority = new FieldDefinition();
        priority.setFieldName("priority");
        priority.setType(FieldType.NUMBER);
        priority.setIndexed(true);

        FieldDefinition city = new FieldDefinition();
        city.setFieldName("city");
        city.setType(FieldType.STRING);
        city.setIndexed(true);

        FieldDefinition profile = new FieldDefinition();
        profile.setFieldName("profile");
        profile.setType(FieldType.OBJECT);
        profile.setSubFields(List.of(city));

        SchemaVersion published = new SchemaVersion();
        published.setEntityName("users");
        published.setVersion(2);
        published.setStatus(SchemaLifecycleStatus.PUBLISHED);
        published.setCreatedAt(LocalDateTime.now());
        published.setFields(List.of(email, priority, profile));
        return published;
    }

    private SchemaVersion publishedSchemaWithUniqueEmailOnly() {
        FieldDefinition email = new FieldDefinition();
        email.setFieldName("email");
        email.setType(FieldType.STRING);
        email.setUnique(true);

        SchemaVersion published = new SchemaVersion();
        published.setEntityName("users");
        published.setVersion(1);
        published.setStatus(SchemaLifecycleStatus.PUBLISHED);
        published.setCreatedAt(LocalDateTime.now());
        published.setFields(List.of(email));
        return published;
    }

    private SchemaVersion publishedSchemaWithoutIndexFlags() {
        FieldDefinition name = new FieldDefinition();
        name.setFieldName("name");
        name.setType(FieldType.STRING);

        SchemaVersion published = new SchemaVersion();
        published.setEntityName("users");
        published.setVersion(1);
        published.setStatus(SchemaLifecycleStatus.PUBLISHED);
        published.setCreatedAt(LocalDateTime.now());
        published.setFields(List.of(name));
        return published;
    }
}
