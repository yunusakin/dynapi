package com.dynapi.domain.validation;

import com.dynapi.domain.exception.ValidationException;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DynamicValidator {
    public void validate(Map<String, Object> data, List<FieldDefinition> schema, Locale locale) {
        for (FieldDefinition field : schema) {
            Object value = data.get(field.getFieldName());

            // Required field validation
            if (field.isRequired() && (value == null || String.valueOf(value).trim().isEmpty())) {
                throw new ValidationException(field.getFieldName(), "Field is required");
            }

            if (value != null) {
                // Type validation
                validateType(field, value);

                // Subfields validation if type is OBJECT
                if (field.getType() == FieldType.OBJECT && field.getSubFields() != null) {
                    if (!(value instanceof Map)) {
                        throw new ValidationException(field.getFieldName(), "Must be an object");
                    }
                    @SuppressWarnings("unchecked")
                    Map<String, Object> objectValue = (Map<String, Object>) value;
                    validate(objectValue, field.getSubFields(), locale);
                }

                // Array validation if type is ARRAY
                if (field.getType() == FieldType.ARRAY && field.getSubFields() != null && !field.getSubFields().isEmpty()) {
                    if (!(value instanceof List)) {
                        throw new ValidationException(field.getFieldName(), "Must be an array");
                    }
                    for (Object item : (List<?>) value) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> mapItem = (Map<String, Object>) item;
                            validate(mapItem, field.getSubFields(), locale);
                        }
                    }
                }
            }
        }
    }

    private void validateType(FieldDefinition field, Object value) {
        switch (field.getType()) {
            case STRING -> {
                if (!(value instanceof String)) {
                    throw new ValidationException(field.getFieldName(), "Must be a string");
                }
            }
            case NUMBER -> {
                if (!(value instanceof Number)) {
                    throw new ValidationException(field.getFieldName(), "Must be a number");
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw new ValidationException(field.getFieldName(), "Must be a boolean");
                }
            }
            case DATE -> {
                if (!(value instanceof String)) {
                    throw new ValidationException(field.getFieldName(), "Must be a date string");
                }
                // Additional date parsing logic can be added here
            }
            case OBJECT, ARRAY -> {
                // Handled in validate method
            }
            default -> throw new ValidationException(field.getFieldName(), "Unsupported field type");
        }
    }
}
