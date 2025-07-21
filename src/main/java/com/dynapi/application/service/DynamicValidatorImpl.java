package com.dynapi.application.service;

import com.dynapi.domain.service.DynamicValidator;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.exception.ValidationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class DynamicValidatorImpl implements DynamicValidator {

    @Override
    public void validate(Map<String, Object> data, List<FieldDefinition> schema) {
        if (data == null || schema == null) {
            throw new ValidationException("root", "Data and schema must not be null");
        }

        for (FieldDefinition field : schema) {
            validateField(field, data.get(field.getFieldName()));
        }
    }

    private void validateField(FieldDefinition field, Object value) {
        if (field.isRequired() && (value == null || String.valueOf(value).trim().isEmpty())) {
            throw new ValidationException(field.getFieldName(), "Field is required");
        }

        if (value != null) {
            validateType(field, value);
            validateNested(field, value);
        }
    }

    private void validateType(FieldDefinition field, Object value) {
        FieldType type = field.getType();
        if (type == FieldType.STRING && !(value instanceof String)) {
            throw new ValidationException(field.getFieldName(), "Must be a string");
        } else if (type == FieldType.NUMBER && !(value instanceof Number)) {
            throw new ValidationException(field.getFieldName(), "Must be a number");
        } else if (type == FieldType.BOOLEAN && !(value instanceof Boolean)) {
            throw new ValidationException(field.getFieldName(), "Must be a boolean");
        } else if (type != FieldType.OBJECT && type != FieldType.ARRAY) {
            throw new ValidationException(field.getFieldName(), "Unsupported field type");
        }
    }

    private void validateNested(FieldDefinition field, Object value) {
        if (field.getType() == FieldType.OBJECT) {
            if (!(value instanceof Map)) {
                throw new ValidationException(field.getFieldName(), "Must be an object");
            }
            if (field.getSubFields() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> objectValue = (Map<String, Object>) value;
                validate(objectValue, field.getSubFields());
            }
        } else if (field.getType() == FieldType.ARRAY) {
            if (!(value instanceof List)) {
                throw new ValidationException(field.getFieldName(), "Must be an array");
            }
            if (field.getSubFields() != null && !field.getSubFields().isEmpty()) {
                List<?> listValue = (List<?>) value;
                for (Object item : listValue) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapItem = (Map<String, Object>) item;
                        validate(mapItem, field.getSubFields());
                    }
                }
            }
        }
    }
}
