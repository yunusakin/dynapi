package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.domain.event.DomainEvent;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.infrastructure.messaging.EventPublisher;
import com.dynapi.repository.FieldDefinitionRepository;
import com.dynapi.repository.FieldGroupRepository;
import com.dynapi.repository.SchemaVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaLifecycleServiceTest {

  @Mock private FieldGroupRepository fieldGroupRepository;
  @Mock private FieldDefinitionRepository fieldDefinitionRepository;
  @Mock private SchemaVersionRepository schemaVersionRepository;
  @Mock private EventPublisher eventPublisher;

  private SchemaLifecycleService schemaLifecycleService;

  @BeforeEach
  void setUp() {
    schemaLifecycleService =
        new SchemaLifecycleService(
            fieldGroupRepository,
            fieldDefinitionRepository,
            schemaVersionRepository,
            eventPublisher);

    lenient()
        .when(schemaVersionRepository.save(any(SchemaVersion.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void publish_createsVersionOneWhenNoPublishedSchemaExists() {
    FieldGroup group = group("task-form", "tasks", List.of("title"));
    FieldDefinition title = field("title", FieldType.STRING, true);

    when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.of(group));
    when(fieldDefinitionRepository.findByFieldNameIn(group.getFieldNames()))
        .thenReturn(List.of(title));
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.empty());

    SchemaVersion published = schemaLifecycleService.publish("task-form");

    assertEquals(1, published.getVersion());
    assertEquals(SchemaLifecycleStatus.PUBLISHED, published.getStatus());
    assertEquals("tasks", published.getEntityName());
    assertEquals("task-form", published.getGroupName());
    assertEquals(1, published.getFields().size());

    verify(eventPublisher).publishSchemaChange(any());
    verify(schemaVersionRepository, times(1)).save(any(SchemaVersion.class));
  }

  @Test
  void publish_resolvesGroupByNameWhenIdLookupMisses() {
    FieldGroup group = group("task-form", "tasks", List.of("title"));
    FieldDefinition title = field("title", FieldType.STRING, true);

    when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.empty());
    when(fieldGroupRepository.findTopByNameOrderByVersionDesc("task-form"))
        .thenReturn(Optional.of(group));
    when(fieldDefinitionRepository.findByFieldNameIn(group.getFieldNames()))
        .thenReturn(List.of(title));
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.empty());

    SchemaVersion published = schemaLifecycleService.publish("task-form");

    assertEquals(1, published.getVersion());
    assertEquals("task-form", published.getGroupName());
  }

  @Test
  void publish_createsNextVersionAndDeprecatesPreviousWhenCompatible() {
    FieldGroup group = group("task-form", "tasks", List.of("title", "description"));
    FieldDefinition title = field("title", FieldType.STRING, true);
    FieldDefinition description = field("description", FieldType.STRING, false);

    SchemaVersion previous = schemaVersion(1, List.of(field("title", FieldType.STRING, true)));

    when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.of(group));
    when(fieldDefinitionRepository.findByFieldNameIn(group.getFieldNames()))
        .thenReturn(List.of(title, description));
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.of(previous));

    SchemaVersion published = schemaLifecycleService.publish("task-form");

    assertEquals(2, published.getVersion());
    assertEquals(SchemaLifecycleStatus.PUBLISHED, published.getStatus());

    ArgumentCaptor<SchemaVersion> saveCaptor = ArgumentCaptor.forClass(SchemaVersion.class);
    verify(schemaVersionRepository, times(2)).save(saveCaptor.capture());
    List<SchemaVersion> saved = saveCaptor.getAllValues();
    assertEquals(SchemaLifecycleStatus.DEPRECATED, saved.get(0).getStatus());
    assertEquals(SchemaLifecycleStatus.PUBLISHED, saved.get(1).getStatus());
  }

  @Test
  void publish_rejectsRemovedFieldPath() {
    runBreakingPublishScenario(
        List.of(field("title", FieldType.STRING, true), field("priority", FieldType.NUMBER, false)),
        List.of(field("title", FieldType.STRING, true)),
        "removed field path");
  }

  @Test
  void publish_rejectsTypeChange() {
    runBreakingPublishScenario(
        List.of(field("priority", FieldType.NUMBER, false)),
        List.of(field("priority", FieldType.STRING, false)),
        "type changed");
  }

  @Test
  void publish_rejectsOptionalFieldBecomingRequired() {
    runBreakingPublishScenario(
        List.of(field("title", FieldType.STRING, false)),
        List.of(field("title", FieldType.STRING, true)),
        "became required");
  }

  @Test
  void publish_rejectsNewRequiredField() {
    runBreakingPublishScenario(
        List.of(field("title", FieldType.STRING, true)),
        List.of(field("title", FieldType.STRING, true), field("age", FieldType.NUMBER, true)),
        "new required field");
  }

  @Test
  void publish_rejectsEnumNarrowing() {
    FieldDefinition previous = field("status", FieldType.STRING, true);
    previous.setEnumValues(List.of("NEW", "DONE"));

    FieldDefinition candidate = field("status", FieldType.STRING, true);
    candidate.setEnumValues(List.of("NEW"));

    runBreakingPublishScenario(List.of(previous), List.of(candidate), "enum narrowed");
  }

  @Test
  void publish_rejectsMinTightening() {
    FieldDefinition previous = field("score", FieldType.NUMBER, false);
    previous.setMin(1.0);

    FieldDefinition candidate = field("score", FieldType.NUMBER, false);
    candidate.setMin(2.0);

    runBreakingPublishScenario(List.of(previous), List.of(candidate), "min tightened");
  }

  @Test
  void publish_rejectsMaxTightening() {
    FieldDefinition previous = field("score", FieldType.NUMBER, false);
    previous.setMax(10.0);

    FieldDefinition candidate = field("score", FieldType.NUMBER, false);
    candidate.setMax(9.0);

    runBreakingPublishScenario(List.of(previous), List.of(candidate), "max tightened");
  }

  @Test
  void publish_rejectsRegexChange() {
    FieldDefinition previous = field("code", FieldType.STRING, false);
    previous.setRegex("^[A-Z]+$");

    FieldDefinition candidate = field("code", FieldType.STRING, false);
    candidate.setRegex("^[A-Z0-9]+$");

    runBreakingPublishScenario(List.of(previous), List.of(candidate), "regex changed");
  }

  @Test
  void publish_rejectsRegexAddedToExistingField() {
    FieldDefinition previous = field("code", FieldType.STRING, false);
    previous.setRegex(null);

    FieldDefinition candidate = field("code", FieldType.STRING, false);
    candidate.setRegex("^[A-Z]+$");

    runBreakingPublishScenario(List.of(previous), List.of(candidate), "regex changed");
  }

  @Test
  void deprecate_rejectsWhenNoPublishedSchemaExists() {
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.empty());

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> schemaLifecycleService.deprecate("tasks"));

    assertTrue(ex.getMessage().contains("No published schema"));
  }

  @Test
  void deprecate_marksLatestPublishedAsDeprecated() {
    SchemaVersion published = schemaVersion(3, List.of(field("title", FieldType.STRING, true)));
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.of(published));

    SchemaVersion deprecated = schemaLifecycleService.deprecate("tasks");

    assertEquals(SchemaLifecycleStatus.DEPRECATED, deprecated.getStatus());
    verify(eventPublisher).publishSchemaChange(any());
  }

  @Test
  void rollback_createsNewPublishedVersionAndDeprecatesCurrentPublished() {
    SchemaVersion target = schemaVersion(1, List.of(field("title", FieldType.STRING, true)));
    target.setStatus(SchemaLifecycleStatus.DEPRECATED);

    SchemaVersion currentPublished =
        schemaVersion(3, List.of(field("title", FieldType.STRING, true)));
    currentPublished.setStatus(SchemaLifecycleStatus.PUBLISHED);

    when(schemaVersionRepository.findByEntityNameAndVersion("tasks", 1))
        .thenReturn(Optional.of(target));
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.of(currentPublished));
    when(schemaVersionRepository.findTopByEntityNameOrderByVersionDesc("tasks"))
        .thenReturn(Optional.of(currentPublished));

    SchemaVersion rolledBack = schemaLifecycleService.rollback("tasks", 1);

    assertEquals(4, rolledBack.getVersion());
    assertEquals(SchemaLifecycleStatus.PUBLISHED, rolledBack.getStatus());
    assertEquals("tasks", rolledBack.getEntityName());

    ArgumentCaptor<SchemaVersion> saveCaptor = ArgumentCaptor.forClass(SchemaVersion.class);
    verify(schemaVersionRepository, times(2)).save(saveCaptor.capture());
    List<SchemaVersion> savedSnapshots = saveCaptor.getAllValues();
    assertEquals(SchemaLifecycleStatus.DEPRECATED, savedSnapshots.get(0).getStatus());
    assertEquals(3, savedSnapshots.get(0).getVersion());
    assertEquals(SchemaLifecycleStatus.PUBLISHED, savedSnapshots.get(1).getStatus());
    assertEquals(4, savedSnapshots.get(1).getVersion());

    ArgumentCaptor<DomainEvent<?>> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(eventPublisher).publishSchemaChange(eventCaptor.capture());
    assertEquals("SCHEMA_ROLLED_BACK", eventCaptor.getValue().getEventType());
  }

  @Test
  void rollback_createsPublishedVersionWhenNoCurrentPublishedSchemaExists() {
    SchemaVersion target = schemaVersion(1, List.of(field("title", FieldType.STRING, true)));
    target.setStatus(SchemaLifecycleStatus.DEPRECATED);
    SchemaVersion latestKnown = schemaVersion(5, List.of(field("title", FieldType.STRING, true)));
    latestKnown.setStatus(SchemaLifecycleStatus.DEPRECATED);

    when(schemaVersionRepository.findByEntityNameAndVersion("tasks", 1))
        .thenReturn(Optional.of(target));
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.empty());
    when(schemaVersionRepository.findTopByEntityNameOrderByVersionDesc("tasks"))
        .thenReturn(Optional.of(latestKnown));

    SchemaVersion rolledBack = schemaLifecycleService.rollback("tasks", 1);

    assertEquals(6, rolledBack.getVersion());
    assertEquals(SchemaLifecycleStatus.PUBLISHED, rolledBack.getStatus());
    verify(schemaVersionRepository, times(1)).save(any(SchemaVersion.class));
    verify(eventPublisher).publishSchemaChange(any());
  }

  @Test
  void rollback_rejectsWhenRequestedVersionDoesNotExist() {
    when(schemaVersionRepository.findByEntityNameAndVersion("tasks", 99))
        .thenReturn(Optional.empty());

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> schemaLifecycleService.rollback("tasks", 99));

    assertTrue(ex.getMessage().contains("Schema version not found"));
  }

  @Test
  void rollback_returnsCurrentPublishedWhenTargetVersionAlreadyPublished() {
    SchemaVersion currentPublished =
        schemaVersion(2, List.of(field("title", FieldType.STRING, true)));
    currentPublished.setStatus(SchemaLifecycleStatus.PUBLISHED);

    when(schemaVersionRepository.findByEntityNameAndVersion("tasks", 2))
        .thenReturn(Optional.of(currentPublished));
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.of(currentPublished));

    SchemaVersion rolledBack = schemaLifecycleService.rollback("tasks", 2);

    assertEquals(currentPublished, rolledBack);
    verify(schemaVersionRepository, times(0)).save(any(SchemaVersion.class));
  }

  private void runBreakingPublishScenario(
      List<FieldDefinition> previousFields,
      List<FieldDefinition> candidateFields,
      String expectedMessage) {
    FieldGroup group =
        group(
            "task-form",
            "tasks",
            candidateFields.stream().map(FieldDefinition::getFieldName).toList());
    SchemaVersion previous = schemaVersion(1, previousFields);

    when(fieldGroupRepository.findById("task-form")).thenReturn(Optional.of(group));
    when(fieldDefinitionRepository.findByFieldNameIn(group.getFieldNames()))
        .thenReturn(candidateFields);
    when(schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            "tasks", SchemaLifecycleStatus.PUBLISHED))
        .thenReturn(Optional.of(previous));

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> schemaLifecycleService.publish("task-form"));

    assertTrue(
        ex.getMessage().contains(expectedMessage),
        "Expected message to contain: " + expectedMessage);
  }

  private FieldGroup group(String name, String entity, List<String> fieldNames) {
    FieldGroup group = new FieldGroup();
    group.setName(name);
    group.setEntity(entity);
    group.setFieldNames(fieldNames);
    return group;
  }

  private SchemaVersion schemaVersion(int version, List<FieldDefinition> fields) {
    SchemaVersion previous = new SchemaVersion();
    previous.setEntityName("tasks");
    previous.setGroupName("task-form");
    previous.setVersion(version);
    previous.setStatus(SchemaLifecycleStatus.PUBLISHED);
    previous.setFields(fields);
    return previous;
  }

  private FieldDefinition field(String name, FieldType type, boolean required) {
    FieldDefinition definition = new FieldDefinition();
    definition.setFieldName(name);
    definition.setType(type);
    definition.setRequired(required);
    return definition;
  }
}
