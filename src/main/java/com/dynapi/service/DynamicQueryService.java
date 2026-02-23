package com.dynapi.service;

import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.FieldType;
import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FilterRule;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.repository.FieldDefinitionRepository;
import com.dynapi.repository.FieldGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class DynamicQueryService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;

    private static final Set<String> COMBINATOR_OPERATORS = Set.of("and", "or", "not");
    private static final Set<String> NUMBER_OPERATORS = Set.of("eq", "ne", "gt", "lt", "gte", "lte", "in");
    private static final Set<String> DATE_OPERATORS = Set.of("eq", "ne", "gt", "lt", "gte", "lte", "in");
    private static final Set<String> STRING_OPERATORS = Set.of("eq", "ne", "in", "regex");
    private static final Set<String> BOOLEAN_OPERATORS = Set.of("eq", "ne", "in");
    private static final Set<String> OBJECT_ARRAY_OPERATORS = Set.of("eq", "ne");

    private final MongoTemplate mongoTemplate;
    private final FieldGroupRepository fieldGroupRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final QueryGuardrailProperties guardrailProperties;

    public PaginatedResponse<FormRecordDto> query(String entity, DynamicQueryRequest request) {
        DynamicQueryRequest safeRequest = request == null ? new DynamicQueryRequest() : request;
        int page = resolvePage(safeRequest.getPage());
        int size = resolveSize(safeRequest.getSize());
        Map<String, FieldType> allowedFieldTypes = loadFieldTypesByEntity(entity);

        validateSort(safeRequest.getSortBy(), safeRequest.getSortDirection(), allowedFieldTypes);
        validateFilters(safeRequest.getFilters(), allowedFieldTypes);

        Query query = new Query();
        if (safeRequest.getFilters() != null && !safeRequest.getFilters().isEmpty()) {
            query.addCriteria(buildCriteria(safeRequest.getFilters()));
        }

        if (safeRequest.getSortBy() != null && !safeRequest.getSortBy().isBlank()) {
            Sort.Direction dir = resolveSortDirection(safeRequest.getSortDirection());
            query.with(Sort.by(dir, safeRequest.getSortBy().trim()));
        }

        query.with(PageRequest.of(page, size));

        List<Map> results = mongoTemplate.find(query, Map.class, entity);
        List<FormRecordDto> content = results.stream().map(result -> {
            FormRecordDto dto = new FormRecordDto();
            dto.setId(result.get("_id") != null ? result.get("_id").toString() : null);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = new HashMap<>((Map<String, Object>) result);
            data.remove("_id");
            data.remove("_class");
            dto.setData(data);
            return dto;
        }).collect(Collectors.toList());

        long total = mongoTemplate.count(query.skip(-1).limit(-1), entity);

        PaginatedResponse<FormRecordDto> response = new PaginatedResponse<>();
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(total);
        response.setContent(content);
        response.setSortBy(safeRequest.getSortBy());
        response.setSortDirection(safeRequest.getSortDirection());
        return response;
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return DEFAULT_PAGE;
        }
        if (page < 0) {
            throw new IllegalArgumentException("Page must be >= 0");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be > 0");
        }
        if (size > guardrailProperties.getMaxPageSize()) {
            throw new IllegalArgumentException("Size exceeds max page size: " + guardrailProperties.getMaxPageSize());
        }
        return size;
    }

    private Sort.Direction resolveSortDirection(String sortDirection) {
        if (sortDirection == null || sortDirection.isBlank()) {
            return Sort.Direction.ASC;
        }

        if ("DESC".equalsIgnoreCase(sortDirection)) {
            return Sort.Direction.DESC;
        }
        if ("ASC".equalsIgnoreCase(sortDirection)) {
            return Sort.Direction.ASC;
        }
        throw new IllegalArgumentException("Unsupported sort direction: " + sortDirection);
    }

    private Map<String, FieldType> loadFieldTypesByEntity(String entity) {
        FieldGroup fieldGroup = fieldGroupRepository.findByEntity(entity)
                .orElseThrow(() -> new IllegalArgumentException("Schema group not found for entity: " + entity));

        List<String> fieldNames = fieldGroup.getFieldNames();
        if (fieldNames == null || fieldNames.isEmpty()) {
            throw new IllegalArgumentException("Field group has no fields for entity: " + entity);
        }

        List<FieldDefinition> definitions = StreamSupport
                .stream(fieldDefinitionRepository.findAllById(fieldNames).spliterator(), false)
                .collect(Collectors.toList());

        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("No field definitions found for entity: " + entity);
        }

        Map<String, FieldType> fieldTypeByPath = new HashMap<>();
        for (FieldDefinition definition : definitions) {
            registerFieldPaths(definition, "", fieldTypeByPath);
        }
        return fieldTypeByPath;
    }

    private void registerFieldPaths(FieldDefinition definition, String parentPath, Map<String, FieldType> fieldTypeByPath) {
        String path = parentPath.isEmpty()
                ? definition.getFieldName()
                : parentPath + "." + definition.getFieldName();

        fieldTypeByPath.put(path, definition.getType());
        if ((definition.getType() == FieldType.OBJECT || definition.getType() == FieldType.ARRAY)
                && definition.getSubFields() != null && !definition.getSubFields().isEmpty()) {
            for (FieldDefinition subField : definition.getSubFields()) {
                registerFieldPaths(subField, path, fieldTypeByPath);
            }
        }
    }

    private void validateSort(String sortBy, String sortDirection, Map<String, FieldType> fieldTypes) {
        if (sortBy == null || sortBy.isBlank()) {
            if (sortDirection != null && !sortDirection.isBlank()) {
                throw new IllegalArgumentException("sortDirection requires sortBy");
            }
            return;
        }

        String normalizedSortBy = sortBy.trim();
        if (!fieldTypes.containsKey(normalizedSortBy)) {
            throw new IllegalArgumentException("Sorting by field is not allowed: " + normalizedSortBy);
        }

        if (sortDirection != null && !sortDirection.isBlank()) {
            resolveSortDirection(sortDirection);
        }
    }

    private void validateFilters(List<FilterRule> filters, Map<String, FieldType> fieldTypes) {
        if (filters == null || filters.isEmpty()) {
            return;
        }

        AtomicInteger ruleCount = new AtomicInteger();
        for (FilterRule filter : filters) {
            validateRule(filter, 1, ruleCount, fieldTypes);
        }
    }

    private void validateRule(
            FilterRule rule,
            int depth,
            AtomicInteger ruleCount,
            Map<String, FieldType> fieldTypes
    ) {
        if (rule == null) {
            throw new IllegalArgumentException("Filter rule cannot be null");
        }
        if (depth > guardrailProperties.getMaxFilterDepth()) {
            throw new IllegalArgumentException("Filter depth exceeds max: " + guardrailProperties.getMaxFilterDepth());
        }
        if (ruleCount.incrementAndGet() > guardrailProperties.getMaxRuleCount()) {
            throw new IllegalArgumentException("Filter rule count exceeds max: " + guardrailProperties.getMaxRuleCount());
        }

        String operator = normalizeOperator(rule.getOperator());
        if (COMBINATOR_OPERATORS.contains(operator)) {
            List<FilterRule> nestedRules = rule.getRules();
            if (nestedRules == null || nestedRules.isEmpty()) {
                throw new IllegalArgumentException("Combinator operator requires nested rules: " + operator);
            }
            if ("not".equals(operator) && nestedRules.size() != 1) {
                throw new IllegalArgumentException("NOT operator requires exactly one nested rule");
            }
            for (FilterRule nestedRule : nestedRules) {
                validateRule(nestedRule, depth + 1, ruleCount, fieldTypes);
            }
            return;
        }

        if (rule.getRules() != null && !rule.getRules().isEmpty()) {
            throw new IllegalArgumentException("Leaf filter operators cannot include nested rules");
        }

        String field = normalizeField(rule.getField());
        if (field == null) {
            throw new IllegalArgumentException("Filter field is required");
        }

        FieldType fieldType = fieldTypes.get(field);
        if (fieldType == null) {
            throw new IllegalArgumentException("Filtering by field is not allowed: " + field);
        }

        validateOperatorForType(field, operator, fieldType);
        validateOperatorValue(field, operator, fieldType, rule.getValue());
    }

    private void validateOperatorForType(String field, String operator, FieldType fieldType) {
        Set<String> allowedOperators = allowedOperatorsFor(fieldType);
        if (!allowedOperators.contains(operator)) {
            throw new IllegalArgumentException(
                    "Operator '" + operator + "' is not allowed for field '" + field + "' of type " + fieldType
            );
        }
    }

    private Set<String> allowedOperatorsFor(FieldType fieldType) {
        return switch (fieldType) {
            case STRING -> STRING_OPERATORS;
            case NUMBER -> NUMBER_OPERATORS;
            case BOOLEAN -> BOOLEAN_OPERATORS;
            case DATE -> DATE_OPERATORS;
            case OBJECT, ARRAY -> OBJECT_ARRAY_OPERATORS;
        };
    }

    private void validateOperatorValue(String field, String operator, FieldType fieldType, Object value) {
        if ("in".equals(operator)) {
            if (!(value instanceof Collection<?> collection)) {
                throw new IllegalArgumentException("IN operator requires a list for field: " + field);
            }
            validateCollectionValues(field, fieldType, collection);
            return;
        }

        if ("regex".equals(operator)) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Regex operator requires string value for field: " + field);
            }
            return;
        }

        if (fieldType == FieldType.NUMBER && ("gt".equals(operator) || "lt".equals(operator) || "gte".equals(operator) || "lte".equals(operator))) {
            if (!(value instanceof Number)) {
                throw new IllegalArgumentException("Operator '" + operator + "' requires numeric value for field: " + field);
            }
        }

        if (fieldType == FieldType.DATE && ("gt".equals(operator) || "lt".equals(operator) || "gte".equals(operator) || "lte".equals(operator))) {
            if (!(value instanceof String)) {
                throw new IllegalArgumentException("Operator '" + operator + "' requires date-string value for field: " + field);
            }
        }
    }

    private void validateCollectionValues(String field, FieldType fieldType, Collection<?> collection) {
        for (Object item : collection) {
            switch (fieldType) {
                case NUMBER -> {
                    if (!(item instanceof Number)) {
                        throw new IllegalArgumentException("IN operator requires numeric list for field: " + field);
                    }
                }
                case BOOLEAN -> {
                    if (!(item instanceof Boolean)) {
                        throw new IllegalArgumentException("IN operator requires boolean list for field: " + field);
                    }
                }
                case DATE, STRING -> {
                    if (!(item instanceof String)) {
                        throw new IllegalArgumentException("IN operator requires string list for field: " + field);
                    }
                }
                case OBJECT, ARRAY -> throw new IllegalArgumentException("IN operator is not allowed for field: " + field);
            }
        }
    }

    private String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "eq";
        }
        return operator.trim().toLowerCase();
    }

    private String normalizeField(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        return field.trim();
    }

    private Criteria buildCriteria(List<FilterRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return new Criteria();
        }
        List<Criteria> criteriaList = rules.stream().map(this::buildCriteria).collect(Collectors.toList());
        if (criteriaList.size() == 1) {
            return criteriaList.get(0);
        }
        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    private Criteria buildCriteria(FilterRule rule) {
        String operator = normalizeOperator(rule.getOperator());
        if (COMBINATOR_OPERATORS.contains(operator)) {
            List<Criteria> nested = rule.getRules().stream().map(this::buildCriteria).collect(Collectors.toList());
            return switch (operator) {
                case "and" -> new Criteria().andOperator(nested.toArray(new Criteria[0]));
                case "or" -> new Criteria().orOperator(nested.toArray(new Criteria[0]));
                case "not" -> new Criteria().norOperator(nested.get(0));
                default -> new Criteria();
            };
        }

        String field = normalizeField(rule.getField());
        Object value = rule.getValue();
        switch (operator) {
            case "eq":
                return Criteria.where(field).is(value);
            case "ne":
                return Criteria.where(field).ne(value);
            case "gt":
                return Criteria.where(field).gt(value);
            case "lt":
                return Criteria.where(field).lt(value);
            case "gte":
                return Criteria.where(field).gte(value);
            case "lte":
                return Criteria.where(field).lte(value);
            case "in":
                return Criteria.where(field).in((Collection<?>) value);
            case "regex":
                return Criteria.where(field).regex((String) value);
            default:
                return Criteria.where(field).is(value);
        }
    }
}
