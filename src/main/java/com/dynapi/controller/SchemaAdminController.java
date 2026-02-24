package com.dynapi.controller;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.dto.ApiResponse;
import com.dynapi.repository.FieldDefinitionRepository;
import com.dynapi.repository.FieldGroupRepository;
import com.dynapi.service.SchemaLifecycleService;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/admin/schema", version = "1")
@RequiredArgsConstructor
public class SchemaAdminController {
  private final FieldDefinitionRepository fieldDefinitionRepository;
  private final FieldGroupRepository fieldGroupRepository;
  private final SchemaLifecycleService schemaLifecycleService;

  // FieldDefinition CRUD
  @PostMapping("/field-definitions")
  public ApiResponse<FieldDefinition> createFieldDefinition(@RequestBody FieldDefinition def) {
    return ApiResponse.success(fieldDefinitionRepository.save(def), "Created");
  }

  @PutMapping("/field-definitions/{id}")
  public ApiResponse<FieldDefinition> updateFieldDefinition(@PathVariable String id, @RequestBody FieldDefinition def) {
    FieldDefinition current = findFieldDefinitionByName(id).orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + id));
    fieldDefinitionRepository.deleteByFieldName(id);
    def.setFieldName(id);
    def.setVersion(def.getVersion() == null ? ((current.getVersion() == null ? 0 : current.getVersion()) + 1) : def.getVersion());
    return ApiResponse.success(fieldDefinitionRepository.save(def), "Updated");
  }

  @DeleteMapping("/field-definitions/{id}")
  public ApiResponse<Void> deleteFieldDefinition(@PathVariable String id) {
    long deleted = fieldDefinitionRepository.deleteByFieldName(id);
    if (deleted == 0) {
      throw new IllegalArgumentException("Field definition not found: " + id);
    }
    return ApiResponse.success(null, "Deleted");
  }

  @GetMapping("/field-definitions")
  public ApiResponse<List<FieldDefinition>> getAllFieldDefinitions() {
    return ApiResponse.success(fieldDefinitionRepository.findAll(), "Fetched");
  }

  // FieldGroup CRUD
  @PostMapping("/field-groups")
  public ApiResponse<FieldGroup> createFieldGroup(@RequestBody FieldGroup group) {
    return ApiResponse.success(fieldGroupRepository.save(group), "Created");
  }

  @PutMapping("/field-groups/{id}")
  public ApiResponse<FieldGroup> updateFieldGroup(@PathVariable String id, @RequestBody FieldGroup group) {
    FieldGroup current = findFieldGroupByName(id).orElseThrow(() -> new IllegalArgumentException("Field group not found: " + id));
    fieldGroupRepository.deleteByName(id);
    group.setName(id);
    group.setVersion(group.getVersion() == null ? ((current.getVersion() == null ? 0 : current.getVersion()) + 1) : group.getVersion());
    return ApiResponse.success(fieldGroupRepository.save(group), "Updated");
  }

  @DeleteMapping("/field-groups/{id}")
  public ApiResponse<Void> deleteFieldGroup(@PathVariable String id) {
    long deleted = fieldGroupRepository.deleteByName(id);
    if (deleted == 0) {
      throw new IllegalArgumentException("Field group not found: " + id);
    }
    return ApiResponse.success(null, "Deleted");
  }

  @GetMapping("/field-groups")
  public ApiResponse<List<FieldGroup>> getAllFieldGroups() {
    return ApiResponse.success(fieldGroupRepository.findAll(), "Fetched");
  }

  @PostMapping("/field-groups/{groupId}/publish")
  public ApiResponse<SchemaVersion> publishFieldGroup(@PathVariable String groupId) {
    SchemaVersion published = schemaLifecycleService.publish(groupId);
    return ApiResponse.success(published, "Published");
  }

  @PostMapping("/entities/{entity}/deprecate")
  public ApiResponse<SchemaVersion> deprecateEntity(@PathVariable String entity) {
    SchemaVersion deprecated = schemaLifecycleService.deprecate(entity);
    return ApiResponse.success(deprecated, "Deprecated");
  }

  @GetMapping("/entities/{entity}/versions")
  public ApiResponse<List<SchemaVersion>> listEntityVersions(@PathVariable String entity) {
    return ApiResponse.success(schemaLifecycleService.listVersions(entity), "Fetched");
  }

  private Optional<FieldDefinition> findFieldDefinitionByName(String fieldName) {
    Optional<FieldDefinition> byRepository = fieldDefinitionRepository.findTopByFieldNameOrderByVersionDesc(fieldName);
    if (byRepository != null && byRepository.isPresent()) {
      return byRepository;
    }
    return fieldDefinitionRepository.findAll().stream().filter(definition -> fieldName.equals(definition.getFieldName())).max(Comparator.comparingInt(this::fieldVersion));
  }

  private Optional<FieldGroup> findFieldGroupByName(String name) {
    Optional<FieldGroup> byRepository = fieldGroupRepository.findTopByNameOrderByVersionDesc(name);
    if (byRepository != null && byRepository.isPresent()) {
      return byRepository;
    }
    return fieldGroupRepository.findAll().stream().filter(group -> name.equals(group.getName())).max(Comparator.comparingInt(this::groupVersion));
  }

  private int fieldVersion(FieldDefinition definition) {
    return definition.getVersion() == null ? 0 : definition.getVersion();
  }

  private int groupVersion(FieldGroup group) {
    return group.getVersion() == null ? 0 : group.getVersion();
  }
}
