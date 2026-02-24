package com.dynapi.service;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.repository.FieldGroupRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FormSubmissionService {
  private final com.dynapi.audit.AuditPublisher auditPublisher;
  private final FieldGroupRepository fieldGroupRepository;
  private final MongoTemplate mongoTemplate;
  private final MessageSource messageSource;
  private final com.dynapi.domain.validation.DynamicValidator dynamicValidator;
  private final SchemaLifecycleService schemaLifecycleService;

  public void submitForm(FormSubmissionRequest request, Locale locale) {
    // 1. Load schema using group
    Optional<FieldGroup> groupOpt = resolveGroup(request.group());
    if (groupOpt.isEmpty()) {
      throw new IllegalArgumentException(
          messageSource.getMessage("error.group.notfound", null, locale));
    }
    FieldGroup group = groupOpt.get();
    // 2. Load latest published schema snapshot for this entity
    SchemaVersion publishedSchema = schemaLifecycleService.latestPublished(group.getEntity());
    List<FieldDefinition> schema = publishedSchema.getFields();
    if (schema == null || schema.isEmpty()) {
      throw new IllegalArgumentException(
          "Published schema has no fields for entity: " + group.getEntity());
    }
    // 3. Validate input recursively and type-safe
    dynamicValidator.validate(request.data(), schema, locale);
    // 4. Save form data to collection by entity
    String collectionName = group.getEntity();
    mongoTemplate.save(request.data(), collectionName);
    // 5. Audit event
    auditPublisher.publish("FORM_SUBMIT", collectionName, request.data());
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

  private int groupVersion(FieldGroup group) {
    return group.getVersion() == null ? 0 : group.getVersion();
  }
}
