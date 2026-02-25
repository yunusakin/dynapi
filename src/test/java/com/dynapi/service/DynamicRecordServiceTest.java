package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.domain.exception.EntityNotFoundException;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.domain.validation.DynamicValidator;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.RecordMutationRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class DynamicRecordServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private SchemaLifecycleService schemaLifecycleService;
    @Mock
    private DynamicValidator dynamicValidator;
    @Mock
    private UniqueFieldConstraintService uniqueFieldConstraintService;
    @Mock
    private com.dynapi.audit.AuditPublisher auditPublisher;

    private DynamicRecordService dynamicRecordService;

    @BeforeEach
    void setUp() {
        dynamicRecordService =
                new DynamicRecordService(
                        mongoTemplate,
                        schemaLifecycleService,
                        dynamicValidator,
                        uniqueFieldConstraintService,
                        auditPublisher);
    }

    @Test
    void patch_mergesDataAndValidatesAgainstPublishedSchema() {
        ObjectId objectId = new ObjectId();
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("_id", objectId);
        existing.put("title", "Old");
        existing.put("profile", Map.of("age", 30, "city", "Istanbul"));

        SchemaVersion published = publishedSchema();
        Map<String, Object> patchPayload = Map.of("profile", Map.of("age", 31));

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("tasks"))).thenReturn(existing);
        when(schemaLifecycleService.latestPublished("tasks")).thenReturn(published);
        when(mongoTemplate.save(any(Map.class), eq("tasks")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FormRecordDto result =
                dynamicRecordService.patch(
                        "tasks", objectId.toHexString(), new RecordMutationRequest(patchPayload), Locale.US);

        ArgumentCaptor<Map<String, Object>> validateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(dynamicValidator)
                .validate(validateCaptor.capture(), eq(published.getFields()), eq(Locale.US));
        ArgumentCaptor<Map<String, Object>> uniqueCaptor = ArgumentCaptor.forClass(Map.class);
        verify(uniqueFieldConstraintService)
                .validateForUpdate(
                        eq("tasks"), eq(objectId), uniqueCaptor.capture(), eq(published.getFields()));
        Map<String, Object> validatedPayload = validateCaptor.getValue();
        Map<String, Object> uniquePayload = uniqueCaptor.getValue();
        assertEquals("Old", validatedPayload.get("title"));
        assertEquals(validatedPayload, uniquePayload);
        assertTrue(validatedPayload.get("profile") instanceof Map<?, ?>);
        assertEquals(31, ((Map<?, ?>) validatedPayload.get("profile")).get("age"));

        assertEquals(objectId.toHexString(), result.id());
        assertEquals("Old", result.data().get("title"));
        assertEquals(31, ((Map<?, ?>) result.data().get("profile")).get("age"));
        verify(auditPublisher).publish(eq("FORM_PATCH"), eq("tasks"), any(Map.class));
    }

    @Test
    void replace_overwritesRecordAndValidatesAgainstPublishedSchema() {
        ObjectId objectId = new ObjectId();
        Map<String, Object> existing = Map.of("_id", objectId, "title", "Old", "priority", 1);
        Map<String, Object> replacement = Map.of("title", "New", "priority", 5);
        SchemaVersion published = publishedSchema();

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("tasks"))).thenReturn(existing);
        when(schemaLifecycleService.latestPublished("tasks")).thenReturn(published);
        when(mongoTemplate.save(any(Map.class), eq("tasks")))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FormRecordDto result =
                dynamicRecordService.replace(
                        "tasks", objectId.toHexString(), new RecordMutationRequest(replacement), Locale.US);

        verify(dynamicValidator).validate(eq(replacement), eq(published.getFields()), eq(Locale.US));
        verify(uniqueFieldConstraintService)
                .validateForUpdate("tasks", objectId, replacement, published.getFields());
        assertEquals(objectId.toHexString(), result.id());
        assertEquals("New", result.data().get("title"));
        assertEquals(5, result.data().get("priority"));
        verify(auditPublisher).publish(eq("FORM_REPLACE"), eq("tasks"), eq(replacement));
    }

    @Test
    void softDelete_marksRecordAsDeleted() {
        ObjectId objectId = new ObjectId();
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("_id", objectId);
        existing.put("title", "Delete me");

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("tasks"))).thenReturn(existing);

        dynamicRecordService.softDelete("tasks", objectId.toHexString());

        ArgumentCaptor<Map<String, Object>> savedCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mongoTemplate).save(savedCaptor.capture(), eq("tasks"));
        Map<String, Object> saved = savedCaptor.getValue();
        assertEquals(Boolean.TRUE, saved.get("deleted"));
        assertTrue(saved.get("deletedAt") instanceof String);
        verify(auditPublisher).publish(eq("FORM_DELETE"), eq("tasks"), any(Map.class));
    }

    @Test
    void patch_throwsNotFoundWhenRecordMissing() {
        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("tasks"))).thenReturn(null);

        assertThrows(
                EntityNotFoundException.class,
                () ->
                        dynamicRecordService.patch(
                                "tasks",
                                "67bcab1057f003430a530fae",
                                new RecordMutationRequest(Map.of("title", "A")),
                                Locale.US));
    }

    @Test
    void patch_throwsWhenUniqueConstraintFails() {
        ObjectId objectId = new ObjectId();
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("_id", objectId);
        existing.put("title", "Old");
        SchemaVersion published = publishedSchema();
        Map<String, Object> patchPayload = Map.of("title", "Duplicate");

        when(mongoTemplate.findOne(any(Query.class), eq(Map.class), eq("tasks"))).thenReturn(existing);
        when(schemaLifecycleService.latestPublished("tasks")).thenReturn(published);
        doThrow(
                new IllegalArgumentException(
                        "Unique field violation for 'title' with value 'Duplicate'"))
                .when(uniqueFieldConstraintService)
                .validateForUpdate(eq("tasks"), eq(objectId), any(Map.class), eq(published.getFields()));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        dynamicRecordService.patch(
                                "tasks",
                                objectId.toHexString(),
                                new RecordMutationRequest(patchPayload),
                                Locale.US));
    }

    private SchemaVersion publishedSchema() {
        FieldDefinition title = new FieldDefinition();
        title.setFieldName("title");
        title.setType(FieldType.STRING);
        title.setRequired(true);

        FieldDefinition age = new FieldDefinition();
        age.setFieldName("age");
        age.setType(FieldType.NUMBER);

        FieldDefinition city = new FieldDefinition();
        city.setFieldName("city");
        city.setType(FieldType.STRING);

        FieldDefinition profile = new FieldDefinition();
        profile.setFieldName("profile");
        profile.setType(FieldType.OBJECT);
        profile.setSubFields(List.of(age, city));

        FieldDefinition priority = new FieldDefinition();
        priority.setFieldName("priority");
        priority.setType(FieldType.NUMBER);

        SchemaVersion published = new SchemaVersion();
        published.setEntityName("tasks");
        published.setVersion(1);
        published.setStatus(SchemaLifecycleStatus.PUBLISHED);
        published.setCreatedAt(LocalDateTime.now());
        published.setFields(List.of(title, profile, priority));
        return published;
    }
}
