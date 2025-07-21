package com.example.dynapi.domain.validation;

import com.example.dynapi.domain.model.FieldDefinition;
import com.example.dynapi.domain.model.FieldType;
import com.example.dynapi.domain.exception.ValidationException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;

@Component
public class DynamicValidator {
    public void validate(Map<String, Object> data, List<FieldDefinition> schema) {
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
                    validate(objectValue, field.getSubFields());
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
                            validate(mapItem, field.getSubFields());
                        }
                    }
                }
            }
        }
    }

    private void validateType(FieldDefinition field, Object value) {
        switch (field.getType()) {
            case STRING:
                if (!(value instanceof String)) {
                    throw new ValidationException(field.getFieldName(), "Must be a string");
                }
                break;
            case NUMBER:
                if (!(value instanceof Number)) {
                    throw new ValidationException(field.getFieldName(), "Must be a number");
                }
                break;
            case BOOLEAN:
                if (!(value instanceof Boolean)) {
                    throw new ValidationException(field.getFieldName(), "Must be a boolean");
                }
                break;
            case OBJECT:
            case ARRAY:
                // Handled in the main validate method
                break;
            default:
                throw new ValidationException(field.getFieldName(), "Unsupported field type");
        }
    }
}
