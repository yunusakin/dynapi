package com.dynapi.service;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UniqueFieldConstraintService {
    private static final Set<FieldType> UNIQUE_SUPPORTED_TYPES =
            Set.of(FieldType.STRING, FieldType.NUMBER, FieldType.BOOLEAN, FieldType.DATE);

    private final MongoTemplate mongoTemplate;

    public void validateForCreate(String entity, Map<String, Object> data, List<FieldDefinition> schema) {
        validate(entity, data, schema, null);
    }

    public void validateForUpdate(String entity, Object recordId, Map<String, Object> data, List<FieldDefinition> schema) {
        validate(entity, data, schema, recordId);
    }

    private void validate(String entity, Map<String, Object> data, List<FieldDefinition> schema, Object recordId) {
        if (entity == null || entity.isBlank()) {
            throw new IllegalArgumentException("Entity must not be blank");
        }

        if (data == null || data.isEmpty() || schema == null || schema.isEmpty()) {
            return;
        }

        List<UniqueField> uniqueFields = collectUniqueFields(schema, "");
        for (UniqueField uniqueField : uniqueFields) {
            Object value = extractValue(data, uniqueField.path());
            if (value == null) {
                continue;
            }

            Query query = new Query();
            query.addCriteria(Criteria.where(uniqueField.path()).is(value));
            query.addCriteria(Criteria.where("deleted").ne(true));
            if (recordId != null) {
                query.addCriteria(Criteria.where("_id").ne(recordId));
            }

            if (mongoTemplate.exists(query, entity)) {
                throw new IllegalArgumentException(
                        "Unique field violation for '"
                                + uniqueField.path()
                                + "' with value '"
                                + value
                                + "'");
            }
        }
    }

    private List<UniqueField> collectUniqueFields(List<FieldDefinition> fields, String parentPath) {
        List<UniqueField> uniqueFields = new ArrayList<>();
        if (fields == null || fields.isEmpty()) {
            return uniqueFields;
        }

        for (FieldDefinition field : fields) {
            if (field == null || field.getFieldName() == null || field.getFieldName().isBlank()) {
                continue;
            }

            String path =
                    parentPath.isEmpty() ? field.getFieldName() : parentPath + "." + field.getFieldName();

            if (field.isUnique()) {
                ensureUniqueSupported(path, field.getType());
                uniqueFields.add(new UniqueField(path));
            }

            if ((field.getType() == FieldType.OBJECT || field.getType() == FieldType.ARRAY)
                    && field.getSubFields() != null
                    && !field.getSubFields().isEmpty()) {
                uniqueFields.addAll(collectUniqueFields(field.getSubFields(), path));
            }
        }

        return uniqueFields;
    }

    private void ensureUniqueSupported(String path, FieldType type) {
        if (type == null || !UNIQUE_SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "Unique constraint is only supported for STRING, NUMBER, BOOLEAN, DATE fields: '"
                            + path
                            + "'");
        }
    }

    private Object extractValue(Map<String, Object> data, String path) {
        Object current = data;
        String[] segments = path.split("\\.");

        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> mapValue)) {
                return null;
            }
            current = mapValue.get(segment);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private record UniqueField(String path) {
    }
}
