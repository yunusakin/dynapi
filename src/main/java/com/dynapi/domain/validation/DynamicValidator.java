package com.dynapi.domain.validation;

import com.dynapi.domain.exception.ValidationException;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class DynamicValidator {
    public void validate(Map<String, Object> data, List<FieldDefinition> schema, Locale locale) {
        validateObject(data, schema, locale, "");
    }

    private void validateObject(Map<String, Object> data, List<FieldDefinition> schema, Locale locale, String parentPath) {
        for (FieldDefinition field : schema) {
            String fieldPath = parentPath.isEmpty()
                    ? field.getFieldName()
                    : parentPath + "." + field.getFieldName();

            Object value = data.get(field.getFieldName());
            boolean required = field.isRequired() || isConditionallyRequired(field, data);
            if (required && isEmpty(value)) {
                throw new ValidationException(fieldPath, "Field is required");
            }

            if (value == null) {
                continue;
            }

            validateType(field, value, fieldPath);
            validateRules(field, value, fieldPath);

            if (field.getType() == FieldType.OBJECT && field.getSubFields() != null && !field.getSubFields().isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> objectValue = (Map<String, Object>) value;
                validateObject(objectValue, field.getSubFields(), locale, fieldPath);
            }

            if (field.getType() == FieldType.ARRAY && field.getSubFields() != null && !field.getSubFields().isEmpty()) {
                List<?> arrayValue = (List<?>) value;
                for (int i = 0; i < arrayValue.size(); i++) {
                    String itemPath = fieldPath + "[" + i + "]";
                    Object item = arrayValue.get(i);
                    if (!(item instanceof Map<?, ?> itemMapRaw)) {
                        throw new ValidationException(itemPath, "Must be an object");
                    }

                    Map<String, Object> itemMap = mapKeysToString(itemMapRaw, itemPath);
                    validateObject(itemMap, field.getSubFields(), locale, itemPath);
                }
            }
        }
    }

    private void validateType(FieldDefinition field, Object value, String fieldPath) {
        switch (field.getType()) {
            case STRING -> {
                if (!(value instanceof String)) {
                    throw new ValidationException(fieldPath, "Must be a string");
                }
            }
            case NUMBER -> {
                if (!(value instanceof Number)) {
                    throw new ValidationException(fieldPath, "Must be a number");
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof Boolean)) {
                    throw new ValidationException(fieldPath, "Must be a boolean");
                }
            }
            case DATE -> {
                if (!(value instanceof String)) {
                    throw new ValidationException(fieldPath, "Must be a date string");
                }
                // Additional date parsing logic can be added here
            }
            case OBJECT -> {
                if (!(value instanceof Map<?, ?>)) {
                    throw new ValidationException(fieldPath, "Must be an object");
                }
            }
            case ARRAY -> {
                if (!(value instanceof List<?>)) {
                    throw new ValidationException(fieldPath, "Must be an array");
                }
            }
            default -> throw new ValidationException(fieldPath, "Unsupported field type");
        }
    }

    private void validateRules(FieldDefinition field, Object value, String fieldPath) {
        validateRange(field, value, fieldPath);
        validateRegex(field, value, fieldPath);
        validateEnum(field, value, fieldPath);
    }

    private void validateRange(FieldDefinition field, Object value, String fieldPath) {
        if (field.getMin() == null && field.getMax() == null) {
            return;
        }

        if (field.getType() == FieldType.NUMBER) {
            BigDecimal actual = toBigDecimal((Number) value);
            if (field.getMin() != null) {
                BigDecimal min = toBigDecimal(field.getMin());
                if (actual.compareTo(min) < 0) {
                    throw new ValidationException(fieldPath, "Must be >= " + field.getMin());
                }
            }
            if (field.getMax() != null) {
                BigDecimal max = toBigDecimal(field.getMax());
                if (actual.compareTo(max) > 0) {
                    throw new ValidationException(fieldPath, "Must be <= " + field.getMax());
                }
            }
            return;
        }

        if (field.getType() == FieldType.STRING) {
            int length = ((String) value).length();
            if (field.getMin() != null && length < field.getMin()) {
                throw new ValidationException(fieldPath, "Length must be >= " + field.getMin().intValue());
            }
            if (field.getMax() != null && length > field.getMax()) {
                throw new ValidationException(fieldPath, "Length must be <= " + field.getMax().intValue());
            }
            return;
        }

        if (field.getType() == FieldType.ARRAY) {
            int size = ((List<?>) value).size();
            if (field.getMin() != null && size < field.getMin()) {
                throw new ValidationException(fieldPath, "Array size must be >= " + field.getMin().intValue());
            }
            if (field.getMax() != null && size > field.getMax()) {
                throw new ValidationException(fieldPath, "Array size must be <= " + field.getMax().intValue());
            }
        }
    }

    private void validateRegex(FieldDefinition field, Object value, String fieldPath) {
        if (field.getRegex() == null || field.getRegex().isBlank()) {
            return;
        }

        if (!(value instanceof String strValue)) {
            throw new ValidationException(fieldPath, "Regex rule can only be applied to string fields");
        }

        try {
            if (!Pattern.matches(field.getRegex(), strValue)) {
                throw new ValidationException(fieldPath, "Value does not match required pattern");
            }
        } catch (PatternSyntaxException ex) {
            throw new ValidationException(fieldPath, "Invalid regex pattern in schema");
        }
    }

    private void validateEnum(FieldDefinition field, Object value, String fieldPath) {
        if (field.getEnumValues() == null || field.getEnumValues().isEmpty()) {
            return;
        }

        boolean matched = field.getEnumValues().stream().anyMatch(allowed -> valuesEqual(allowed, value));
        if (!matched) {
            throw new ValidationException(fieldPath, "Must be one of allowed enum values");
        }
    }

    private boolean isConditionallyRequired(FieldDefinition field, Map<String, Object> data) {
        FieldDefinition.RequiredIfRule requiredIf = field.getRequiredIf();
        if (requiredIf == null || requiredIf.getField() == null || requiredIf.getField().isBlank()) {
            return false;
        }

        Object left = resolvePath(data, requiredIf.getField());
        Object right = requiredIf.getValue();
        String operator = requiredIf.getOperator() == null
                ? "eq"
                : requiredIf.getOperator().trim().toLowerCase(Locale.ROOT);

        return switch (operator) {
            case "eq" -> valuesEqual(left, right);
            case "ne" -> !valuesEqual(left, right);
            case "in" -> right instanceof Collection<?> expected
                    && expected.stream().anyMatch(candidate -> valuesEqual(left, candidate));
            default -> valuesEqual(left, right);
        };
    }

    private Object resolvePath(Map<String, Object> data, String path) {
        if (path == null || path.isBlank()) {
            return null;
        }

        Object current = data;
        String[] parts = path.split("\\.");
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> currentMap)) {
                return null;
            }
            current = currentMap.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String str) {
            return str.trim().isEmpty();
        }
        if (value instanceof Collection<?> col) {
            return col.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return toBigDecimal(leftNumber).compareTo(toBigDecimal(rightNumber)) == 0;
        }
        if (Objects.equals(left, right)) {
            return true;
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private BigDecimal toBigDecimal(Number value) {
        return new BigDecimal(value.toString());
    }

    private Map<String, Object> mapKeysToString(Map<?, ?> rawMap, String fieldPath) {
        for (Object key : rawMap.keySet()) {
            if (!(key instanceof String)) {
                throw new ValidationException(fieldPath, "Object keys must be strings");
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mapped = (Map<String, Object>) rawMap;
        return mapped;
    }
}
