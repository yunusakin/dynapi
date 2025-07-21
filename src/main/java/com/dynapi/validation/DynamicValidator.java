package com.dynapi.validation;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DynamicValidator {
    public void validate(Map<String, Object> data, List<FieldDefinition> schema, MessageSource messageSource, Locale locale) {
        for (FieldDefinition field : schema) {
            Object value = data.get(field.getFieldName());
            if (field.isRequired() && value == null) {
                throw new IllegalArgumentException(messageSource.getMessage("error.validation", new Object[]{field.getFieldName()}, locale));
            }
            if (value != null) {
                // Type check
                if (!isTypeValid(value, field.getType())) {
                    throw new IllegalArgumentException(messageSource.getMessage("error.validation", new Object[]{field.getFieldName()}, locale));
                }
                // Recursive for subFields
                if (field.getSubFields() != null && !field.getSubFields().isEmpty() && value instanceof Map) {
                    validate((Map<String, Object>) value, field.getSubFields(), messageSource, locale);
                }
            }
        }
    }
    private boolean isTypeValid(Object value, FieldType type) {
        return switch (type) {
            case STRING -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> value instanceof String; // Date parsing can be added
            case OBJECT -> value instanceof Map;
            case ARRAY -> value instanceof List;
        };
    }
}
