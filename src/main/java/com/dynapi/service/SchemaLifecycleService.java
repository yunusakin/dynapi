package com.dynapi.service;

import com.dynapi.domain.event.DomainEvent;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.infrastructure.messaging.EventPublisher;
import com.dynapi.repository.FieldDefinitionRepository;
import com.dynapi.repository.FieldGroupRepository;
import com.dynapi.repository.SchemaVersionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SchemaLifecycleService {
  private final FieldGroupRepository fieldGroupRepository;
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final SchemaVersionRepository schemaVersionRepository;
  private final EventPublisher eventPublisher;

  public SchemaVersion publish(String groupId) {
    FieldGroup group =
        resolveGroup(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Field group not found: " + groupId));
    List<FieldDefinition> draftFields = loadDraftFields(group);

    Optional<SchemaVersion> latestPublishedOpt =
        schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            group.getEntity(), SchemaLifecycleStatus.PUBLISHED);
    latestPublishedOpt.ifPresent(previous -> ensureCompatible(previous, draftFields));

    LocalDateTime now = LocalDateTime.now();
    String actor = currentActor();

    latestPublishedOpt.ifPresent(
        previous -> {
          previous.setStatus(SchemaLifecycleStatus.DEPRECATED);
          previous.setDeprecatedAt(now);
          previous.setModifiedBy(actor);
          previous.setModifiedAt(now);
          schemaVersionRepository.save(previous);
        });

    int nextVersion = latestPublishedOpt.map(version -> version.getVersion() + 1).orElse(1);

    SchemaVersion snapshot = new SchemaVersion();
    snapshot.setEntityName(group.getEntity());
    snapshot.setGroupName(group.getName());
    snapshot.setVersion(nextVersion);
    snapshot.setStatus(SchemaLifecycleStatus.PUBLISHED);
    snapshot.setFields(copyFieldDefinitions(draftFields));
    snapshot.setPublishedAt(now);
    snapshot.setCreatedAt(now);
    snapshot.setCreatedBy(actor);
    snapshot.setModifiedAt(now);
    snapshot.setModifiedBy(actor);

    SchemaVersion saved = schemaVersionRepository.save(snapshot);
    publishSchemaEvent(
        "SCHEMA_PUBLISHED",
        group.getEntity(),
        saved,
        Map.of(
            "groupId",
            groupId,
            "groupName",
            group.getName() == null ? "" : group.getName(),
            "version",
            String.valueOf(saved.getVersion())));
    return saved;
  }

  public SchemaVersion deprecate(String entity) {
    SchemaVersion published =
        schemaVersionRepository
            .findTopByEntityNameAndStatusOrderByVersionDesc(entity, SchemaLifecycleStatus.PUBLISHED)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No published schema found for entity: " + entity));

    LocalDateTime now = LocalDateTime.now();
    published.setStatus(SchemaLifecycleStatus.DEPRECATED);
    published.setDeprecatedAt(now);
    published.setModifiedAt(now);
    published.setModifiedBy(currentActor());

    SchemaVersion saved = schemaVersionRepository.save(published);
    publishSchemaEvent(
        "SCHEMA_DEPRECATED", entity, saved, Map.of("version", String.valueOf(saved.getVersion())));
    return saved;
  }

  public SchemaVersion rollback(String entity, Integer version) {
    if (version == null || version < 1) {
      throw new IllegalArgumentException("Version must be >= 1");
    }

    SchemaVersion target =
        schemaVersionRepository
            .findByEntityNameAndVersion(entity, version)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Schema version not found for entity '"
                            + entity
                            + "' and version "
                            + version));

    Optional<SchemaVersion> currentPublishedOpt =
        schemaVersionRepository.findTopByEntityNameAndStatusOrderByVersionDesc(
            entity, SchemaLifecycleStatus.PUBLISHED);
    if (currentPublishedOpt.isPresent()
        && Objects.equals(currentPublishedOpt.get().getVersion(), version)) {
      return currentPublishedOpt.get();
    }

    LocalDateTime now = LocalDateTime.now();
    String actor = currentActor();

    currentPublishedOpt.ifPresent(
        currentPublished -> {
          currentPublished.setStatus(SchemaLifecycleStatus.DEPRECATED);
          currentPublished.setDeprecatedAt(now);
          currentPublished.setModifiedAt(now);
          currentPublished.setModifiedBy(actor);
          schemaVersionRepository.save(currentPublished);
        });

    int nextVersion =
        schemaVersionRepository
                .findTopByEntityNameOrderByVersionDesc(entity)
                .map(this::schemaVersionNumber)
                .orElse(0)
            + 1;

    SchemaVersion rolledBack = new SchemaVersion();
    rolledBack.setEntityName(target.getEntityName());
    rolledBack.setGroupName(target.getGroupName());
    rolledBack.setVersion(nextVersion);
    rolledBack.setStatus(SchemaLifecycleStatus.PUBLISHED);
    rolledBack.setFields(copyFieldDefinitions(target.getFields()));
    rolledBack.setPublishedAt(now);
    rolledBack.setCreatedAt(now);
    rolledBack.setCreatedBy(actor);
    rolledBack.setModifiedAt(now);
    rolledBack.setModifiedBy(actor);

    SchemaVersion saved = schemaVersionRepository.save(rolledBack);
    publishSchemaEvent(
        "SCHEMA_ROLLED_BACK",
        entity,
        saved,
        Map.of(
            "fromVersion", String.valueOf(version),
            "toVersion", String.valueOf(saved.getVersion())));
    return saved;
  }

  public List<SchemaVersion> listVersions(String entity) {
    return schemaVersionRepository.findByEntityNameOrderByVersionDesc(entity);
  }

  public SchemaVersion latestPublished(String entity) {
    return schemaVersionRepository
        .findTopByEntityNameAndStatusOrderByVersionDesc(entity, SchemaLifecycleStatus.PUBLISHED)
        .orElseThrow(
            () -> new IllegalArgumentException("No published schema found for entity: " + entity));
  }

  private List<FieldDefinition> loadDraftFields(FieldGroup group) {
    List<String> fieldNames = group.getFieldNames();
    if (fieldNames == null || fieldNames.isEmpty()) {
      throw new IllegalArgumentException("Field group has no fields: " + group.getName());
    }

    List<FieldDefinition> definitions = fieldDefinitionRepository.findByFieldNameIn(fieldNames);
    if (definitions == null || definitions.isEmpty()) {
      definitions =
          fieldDefinitionRepository.findAll().stream()
              .filter(
                  definition ->
                      definition.getFieldName() != null
                          && fieldNames.contains(definition.getFieldName()))
              .toList();
    }
    Map<String, FieldDefinition> latestByName = new HashMap<>();
    for (FieldDefinition definition : definitions) {
      if (definition == null || definition.getFieldName() == null) {
        continue;
      }
      FieldDefinition existing = latestByName.get(definition.getFieldName());
      if (existing == null || fieldVersion(definition) >= fieldVersion(existing)) {
        latestByName.put(definition.getFieldName(), definition);
      }
    }

    List<FieldDefinition> ordered = new ArrayList<>();
    for (String fieldName : fieldNames) {
      FieldDefinition definition = latestByName.get(fieldName);
      if (definition == null) {
        throw new IllegalArgumentException("Field definition not found: " + fieldName);
      }
      ordered.add(definition);
    }
    return ordered;
  }

  private void ensureCompatible(
      SchemaVersion previousPublished, List<FieldDefinition> candidateFields) {
    Map<String, FieldDescriptor> previous = flattenDescriptors(previousPublished.getFields());
    Map<String, FieldDescriptor> candidate = flattenDescriptors(candidateFields);

    for (String previousPath : previous.keySet()) {
      if (!candidate.containsKey(previousPath)) {
        throw new IllegalArgumentException(
            "Breaking change: removed field path '" + previousPath + "'");
      }
    }

    for (Map.Entry<String, FieldDescriptor> candidateEntry : candidate.entrySet()) {
      String path = candidateEntry.getKey();
      FieldDescriptor next = candidateEntry.getValue();
      FieldDescriptor prev = previous.get(path);

      if (prev == null) {
        if (next.required) {
          throw new IllegalArgumentException("Breaking change: new required field '" + path + "'");
        }
        continue;
      }

      if (prev.type != next.type) {
        throw new IllegalArgumentException(
            "Breaking change: type changed for field '" + path + "'");
      }

      if (!prev.required && next.required) {
        throw new IllegalArgumentException(
            "Breaking change: optional field became required '" + path + "'");
      }

      if (isEnumNarrowed(prev.enumValues, next.enumValues)) {
        throw new IllegalArgumentException(
            "Breaking change: enum narrowed for field '" + path + "'");
      }

      if (isMinTightened(prev.min, next.min)) {
        throw new IllegalArgumentException(
            "Breaking change: min tightened for field '" + path + "'");
      }

      if (isMaxTightened(prev.max, next.max)) {
        throw new IllegalArgumentException(
            "Breaking change: max tightened for field '" + path + "'");
      }

      if (isRegexChanged(prev.regex, next.regex)) {
        throw new IllegalArgumentException(
            "Breaking change: regex changed for field '" + path + "'");
      }
    }
  }

  private boolean isEnumNarrowed(List<Object> previous, List<Object> next) {
    if (previous == null || previous.isEmpty()) {
      return false;
    }
    if (next == null || next.isEmpty()) {
      return false;
    }
    for (Object oldValue : previous) {
      boolean exists = next.stream().anyMatch(candidate -> valuesEqual(candidate, oldValue));
      if (!exists) {
        return true;
      }
    }
    return false;
  }

  private boolean isMinTightened(Double previous, Double next) {
    if (previous == null && next == null) {
      return false;
    }
    if (previous == null && next != null) {
      return true;
    }
    if (previous != null && next == null) {
      return false;
    }
    return next > previous;
  }

  private boolean isMaxTightened(Double previous, Double next) {
    if (previous == null && next == null) {
      return false;
    }
    if (previous == null && next != null) {
      return true;
    }
    if (previous != null && next == null) {
      return false;
    }
    return next < previous;
  }

  private boolean isRegexChanged(String previous, String next) {
    String left = normalize(previous);
    String right = normalize(next);
    return !Objects.equals(left, right);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private boolean valuesEqual(Object left, Object right) {
    if (left == null || right == null) {
      return Objects.equals(left, right);
    }
    return String.valueOf(left).equals(String.valueOf(right));
  }

  private Optional<FieldGroup> resolveGroup(String groupIdOrName) {
    Optional<FieldGroup> byId = fieldGroupRepository.findById(groupIdOrName);
    if (byId != null && byId.isPresent()) {
      return byId;
    }
    Optional<FieldGroup> byName =
        fieldGroupRepository.findTopByNameOrderByVersionDesc(groupIdOrName);
    if (byName != null && byName.isPresent()) {
      return byName;
    }
    return fieldGroupRepository.findAll().stream()
        .filter(
            group -> groupIdOrName.equals(group.getName()) || groupIdOrName.equals(group.getId()))
        .max(Comparator.comparingInt(this::groupVersion));
  }

  private int fieldVersion(FieldDefinition definition) {
    return definition.getVersion() == null ? 0 : definition.getVersion();
  }

  private int groupVersion(FieldGroup group) {
    return group.getVersion() == null ? 0 : group.getVersion();
  }

  private int schemaVersionNumber(SchemaVersion schemaVersion) {
    return schemaVersion.getVersion() == null ? 0 : schemaVersion.getVersion();
  }

  private Map<String, FieldDescriptor> flattenDescriptors(List<FieldDefinition> fields) {
    Map<String, FieldDescriptor> descriptors = new LinkedHashMap<>();
    if (fields == null) {
      return descriptors;
    }
    for (FieldDefinition field : fields) {
      addDescriptor(field, "", descriptors);
    }
    return descriptors;
  }

  private void addDescriptor(
      FieldDefinition field, String parentPath, Map<String, FieldDescriptor> out) {
    String path =
        parentPath.isEmpty() ? field.getFieldName() : parentPath + "." + field.getFieldName();

    out.put(
        path,
        new FieldDescriptor(
            field.getType(),
            field.isRequired(),
            field.getEnumValues() == null ? null : new ArrayList<>(field.getEnumValues()),
            field.getMin(),
            field.getMax(),
            field.getRegex()));

    if (field.getSubFields() == null || field.getSubFields().isEmpty()) {
      return;
    }
    for (FieldDefinition child : field.getSubFields()) {
      addDescriptor(child, path, out);
    }
  }

  private List<FieldDefinition> copyFieldDefinitions(List<FieldDefinition> source) {
    if (source == null) {
      return List.of();
    }
    return source.stream().map(this::copyFieldDefinition).collect(Collectors.toList());
  }

  private FieldDefinition copyFieldDefinition(FieldDefinition source) {
    FieldDefinition target = new FieldDefinition();
    target.setFieldName(source.getFieldName());
    target.setType(source.getType());
    target.setRequired(source.isRequired());
    target.setMin(source.getMin());
    target.setMax(source.getMax());
    target.setRegex(source.getRegex());
    target.setEnumValues(
        source.getEnumValues() == null ? null : new ArrayList<>(source.getEnumValues()));
    target.setRequiredIf(copyRequiredIf(source.getRequiredIf()));
    target.setSubFields(
        source.getSubFields() == null
            ? null
            : source.getSubFields().stream()
                .map(this::copyFieldDefinition)
                .collect(Collectors.toList()));
    target.setVersion(source.getVersion());
    target.setPermissions(
        source.getPermissions() == null ? null : new ArrayList<>(source.getPermissions()));
    return target;
  }

  private FieldDefinition.RequiredIfRule copyRequiredIf(FieldDefinition.RequiredIfRule requiredIf) {
    if (requiredIf == null) {
      return null;
    }
    FieldDefinition.RequiredIfRule copy = new FieldDefinition.RequiredIfRule();
    copy.setField(requiredIf.getField());
    copy.setValue(requiredIf.getValue());
    copy.setOperator(requiredIf.getOperator());
    return copy;
  }

  private void publishSchemaEvent(
      String eventType, String entity, Object payload, Map<String, String> metadata) {
    DomainEvent<Object> event = new DomainEvent<>();
    event.setEventType(eventType);
    event.setEntityName(entity);
    event.setTimestamp(LocalDateTime.now());
    event.setUserId(currentActor());
    event.setPayload(payload);
    event.setMetadata(new HashMap<>(metadata));
    eventPublisher.publishSchemaChange(event);
  }

  private String currentActor() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || authentication.getName() == null
        || authentication.getName().isBlank()) {
      return "system";
    }
    return authentication.getName();
  }

  private record FieldDescriptor(
      com.dynapi.domain.model.FieldType type,
      boolean required,
      List<Object> enumValues,
      Double min,
      Double max,
      String regex) {}
}
