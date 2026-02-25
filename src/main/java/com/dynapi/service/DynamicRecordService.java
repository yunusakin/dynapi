package com.dynapi.service;

import com.dynapi.domain.exception.EntityNotFoundException;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.domain.validation.DynamicValidator;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.RecordMutationRequest;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DynamicRecordService {
  private static final Set<String> RESERVED_FIELDS =
      Set.of("_id", "_class", "deleted", "deletedAt", "deletedBy");

  private final MongoTemplate mongoTemplate;
  private final SchemaLifecycleService schemaLifecycleService;
  private final DynamicValidator dynamicValidator;
  private final com.dynapi.audit.AuditPublisher auditPublisher;

  public FormRecordDto patch(String entity, String id, RecordMutationRequest request, Locale locale) {
    Map<String, Object> existing = loadActiveRecord(entity, id);
    Map<String, Object> patchData = sanitizeInput(request.data());
    Map<String, Object> merged = deepMerge(extractData(existing), patchData);

    validateAgainstPublishedSchema(entity, merged, locale);

    Map<String, Object> saved = saveRecord(entity, existing.get("_id"), merged);
    auditPublisher.publish("FORM_PATCH", entity, merged);
    return toRecordDto(saved);
  }

  public FormRecordDto replace(
      String entity, String id, RecordMutationRequest request, Locale locale) {
    Map<String, Object> existing = loadActiveRecord(entity, id);
    Map<String, Object> replacement = sanitizeInput(request.data());

    validateAgainstPublishedSchema(entity, replacement, locale);

    Map<String, Object> saved = saveRecord(entity, existing.get("_id"), replacement);
    auditPublisher.publish("FORM_REPLACE", entity, replacement);
    return toRecordDto(saved);
  }

  public void softDelete(String entity, String id) {
    Map<String, Object> existing = loadActiveRecord(entity, id);
    existing.put("deleted", true);
    existing.put("deletedAt", LocalDateTime.now().toString());

    mongoTemplate.save(existing, entity);
    auditPublisher.publish(
        "FORM_DELETE",
        entity,
        Map.of(
            "id",
            existing.get("_id") == null ? id : existing.get("_id").toString(),
            "deleted",
            true));
  }

  private Map<String, Object> saveRecord(String entity, Object id, Map<String, Object> data) {
    Map<String, Object> document = new LinkedHashMap<>(data);
    document.put("_id", id);
    document.remove("_class");
    document.remove("deleted");
    document.remove("deletedAt");
    document.remove("deletedBy");
    return mongoTemplate.save(document, entity);
  }

  private Map<String, Object> loadActiveRecord(String entity, String id) {
    Query query = new Query();
    query.addCriteria(buildIdCriteria(id));
    query.addCriteria(Criteria.where("deleted").ne(true));

    Map<String, Object> existing = mongoTemplate.findOne(query, Map.class, entity);
    if (existing == null) {
      throw new EntityNotFoundException("Record not found for entity '" + entity + "' and id '" + id + "'");
    }
    return existing;
  }

  private Criteria buildIdCriteria(String id) {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("Record id must not be blank");
    }
    if (!ObjectId.isValid(id)) {
      return Criteria.where("_id").is(id);
    }
    return new Criteria()
        .orOperator(Criteria.where("_id").is(new ObjectId(id)), Criteria.where("_id").is(id));
  }

  private void validateAgainstPublishedSchema(String entity, Map<String, Object> data, Locale locale) {
    SchemaVersion published = schemaLifecycleService.latestPublished(entity);
    List<FieldDefinition> schema = published.getFields();
    if (schema == null || schema.isEmpty()) {
      throw new IllegalArgumentException("Published schema has no fields for entity: " + entity);
    }
    dynamicValidator.validate(data, schema, locale);
  }

  private Map<String, Object> sanitizeInput(Map<String, Object> data) {
    if (data == null) {
      throw new IllegalArgumentException("Record data must not be null");
    }

    Map<String, Object> sanitized = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      String key = entry.getKey();
      if (key == null || key.isBlank()) {
        throw new IllegalArgumentException("Record field name must not be blank");
      }
      if (RESERVED_FIELDS.contains(key)) {
        throw new IllegalArgumentException("Reserved field is not allowed in payload: " + key);
      }
      sanitized.put(key, sanitizeValue(entry.getValue()));
    }
    return sanitized;
  }

  private Object sanitizeValue(Object value) {
    if (value instanceof Map<?, ?> mapValue) {
      Map<String, Object> converted = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
        if (!(entry.getKey() instanceof String key)) {
          throw new IllegalArgumentException("Nested object keys must be strings");
        }
        converted.put(key, sanitizeValue(entry.getValue()));
      }
      return converted;
    }

    if (value instanceof Collection<?> collectionValue) {
      return collectionValue.stream().map(this::sanitizeValue).toList();
    }

    return value;
  }

  private Map<String, Object> extractData(Map<String, Object> document) {
    Map<String, Object> data = new LinkedHashMap<>(document);
    data.remove("_id");
    data.remove("_class");
    data.remove("deleted");
    data.remove("deletedAt");
    data.remove("deletedBy");
    return data;
  }

  private Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> patch) {
    Map<String, Object> merged = new LinkedHashMap<>(base);
    for (Map.Entry<String, Object> patchEntry : patch.entrySet()) {
      Object existingValue = merged.get(patchEntry.getKey());
      Object patchValue = patchEntry.getValue();
      if (existingValue instanceof Map<?, ?> existingMap && patchValue instanceof Map<?, ?> patchMap) {
        merged.put(
            patchEntry.getKey(),
            deepMerge(castToStringObjectMap(existingMap), castToStringObjectMap(patchMap)));
      } else {
        merged.put(patchEntry.getKey(), patchValue);
      }
    }
    return merged;
  }

  private Map<String, Object> castToStringObjectMap(Map<?, ?> map) {
    Map<String, Object> converted = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IllegalArgumentException("Nested object keys must be strings");
      }
      converted.put(key, entry.getValue());
    }
    return converted;
  }

  private FormRecordDto toRecordDto(Map<String, Object> document) {
    String id = document.get("_id") == null ? null : document.get("_id").toString();
    Map<String, Object> data = extractData(document);
    return new FormRecordDto(id, data);
  }
}
