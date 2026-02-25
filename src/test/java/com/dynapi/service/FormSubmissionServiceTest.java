package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.repository.FieldGroupRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class FormSubmissionServiceTest {

    @Mock
    private FieldGroupRepository fieldGroupRepository;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private MessageSource messageSource;
    @Mock
    private com.dynapi.domain.validation.DynamicValidator dynamicValidator;
    @Mock
    private SchemaLifecycleService schemaLifecycleService;
    @Mock
    private UniqueFieldConstraintService uniqueFieldConstraintService;

    private FormSubmissionService formSubmissionService;

    @BeforeEach
    void setUp() {
        formSubmissionService =
                new FormSubmissionService(
                        fieldGroupRepository,
                        mongoTemplate,
                        messageSource,
                        dynamicValidator,
                        schemaLifecycleService,
                        uniqueFieldConstraintService);
    }

    @Test
    void submitForm_throwsWhenNoPublishedSchemaExists() {
        FieldGroup group = new FieldGroup();
        group.setName("task-form");
        group.setEntity("tasks");

        FormSubmissionRequest request =
                new FormSubmissionRequest("task-form", Map.of("title", "Ship v1"));

        when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.of(group));
        when(schemaLifecycleService.latestPublished("tasks"))
                .thenThrow(new IllegalArgumentException("No published schema found for entity: tasks"));

        assertThrows(
                IllegalArgumentException.class, () -> formSubmissionService.submitForm(request, Locale.US));
    }

    @Test
    void submitForm_validatesAgainstPublishedSchemaAndPersists() {
        FieldGroup group = new FieldGroup();
        group.setName("task-form");
        group.setEntity("tasks");

        FieldDefinition title = new FieldDefinition();
        title.setFieldName("title");
        title.setType(FieldType.STRING);
        title.setRequired(true);

        SchemaVersion published = new SchemaVersion();
        published.setEntityName("tasks");
        published.setVersion(1);
        published.setStatus(SchemaLifecycleStatus.PUBLISHED);
        published.setCreatedAt(LocalDateTime.now());
        published.setFields(List.of(title));

        Map<String, Object> payload = Map.of("title", "Ship v1");
        FormSubmissionRequest request = new FormSubmissionRequest("task-form", payload);

        when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.of(group));
        when(schemaLifecycleService.latestPublished("tasks")).thenReturn(published);

        formSubmissionService.submitForm(request, Locale.US);

        verify(dynamicValidator).validate(eq(payload), eq(List.of(title)), any(Locale.class));
        verify(uniqueFieldConstraintService).validateForCreate("tasks", payload, List.of(title));
        verify(mongoTemplate).save(payload, "tasks");
    }

    @Test
    void submitForm_resolvesGroupByNameWhenIdLookupMisses() {
        FieldGroup group = new FieldGroup();
        group.setName("task-form");
        group.setEntity("tasks");

        FieldDefinition title = new FieldDefinition();
        title.setFieldName("title");
        title.setType(FieldType.STRING);
        title.setRequired(true);

        SchemaVersion published = new SchemaVersion();
        published.setEntityName("tasks");
        published.setVersion(1);
        published.setStatus(SchemaLifecycleStatus.PUBLISHED);
        published.setCreatedAt(LocalDateTime.now());
        published.setFields(List.of(title));

        Map<String, Object> payload = Map.of("title", "Ship v1");
        FormSubmissionRequest request = new FormSubmissionRequest("task-form", payload);

        when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.empty());
        when(fieldGroupRepository.findTopByNameOrderByVersionDesc("task-form"))
                .thenReturn(Optional.of(group));
        when(schemaLifecycleService.latestPublished("tasks")).thenReturn(published);

        formSubmissionService.submitForm(request, Locale.US);

        verify(dynamicValidator).validate(eq(payload), eq(List.of(title)), any(Locale.class));
        verify(uniqueFieldConstraintService).validateForCreate("tasks", payload, List.of(title));
        verify(mongoTemplate).save(payload, "tasks");
    }

    @Test
    void submitForm_throwsWhenUniqueConstraintFails() {
        FieldGroup group = new FieldGroup();
        group.setName("task-form");
        group.setEntity("tasks");

        FieldDefinition title = new FieldDefinition();
        title.setFieldName("title");
        title.setType(FieldType.STRING);
        title.setRequired(true);
        title.setUnique(true);

        SchemaVersion published = new SchemaVersion();
        published.setEntityName("tasks");
        published.setVersion(1);
        published.setStatus(SchemaLifecycleStatus.PUBLISHED);
        published.setCreatedAt(LocalDateTime.now());
        published.setFields(List.of(title));

        Map<String, Object> payload = Map.of("title", "Duplicate");
        FormSubmissionRequest request = new FormSubmissionRequest("task-form", payload);

        when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.of(group));
        when(schemaLifecycleService.latestPublished("tasks")).thenReturn(published);
        doThrow(
                new IllegalArgumentException(
                        "Unique field violation for 'title' with value 'Duplicate'"))
                .when(uniqueFieldConstraintService)
                .validateForCreate("tasks", payload, List.of(title));

        assertThrows(
                IllegalArgumentException.class, () -> formSubmissionService.submitForm(request, Locale.US));
    }
}
