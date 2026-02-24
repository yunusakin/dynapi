package com.dynapi.service;

import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FilterRule;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DynamicQueryService {
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 10;

  private static final Set<String> COMBINATOR_OPERATORS = Set.of("and", "or", "not");
  private static final Set<String> NUMBER_OPERATORS =
      Set.of("eq", "ne", "gt", "lt", "gte", "lte", "in");
  private static final Set<String> DATE_OPERATORS =
      Set.of("eq", "ne", "gt", "lt", "gte", "lte", "in");
  private static final Set<String> STRING_OPERATORS = Set.of("eq", "ne", "in", "regex");
  private static final Set<String> BOOLEAN_OPERATORS = Set.of("eq", "ne", "in");
  private static final Set<String> OBJECT_ARRAY_OPERATORS = Set.of("eq", "ne");

  private final MongoTemplate mongoTemplate;
  private final SchemaLifecycleService schemaLifecycleService;
  private final QueryGuardrailProperties guardrailProperties;

  public PaginatedResponse<FormRecordDto> query(String entity, DynamicQueryRequest request) {
    DynamicQueryRequest safeRequest =
        request == null ? new DynamicQueryRequest(null, null, null, null, null) : request;
    int page = resolvePage(safeRequest.page());
    int size = resolveSize(safeRequest.size());
    Map<String, FieldType> allowedFieldTypes = loadFieldTypesByEntity(entity);
    List<FilterNode> filterNodes = toFilterNodes(safeRequest.filters());

    validateSort(safeRequest.sortBy(), safeRequest.sortDirection(), allowedFieldTypes);
    validateFilters(filterNodes, allowedFieldTypes);

    Query query = new Query();
    if (!filterNodes.isEmpty()) {
      query.addCriteria(buildCriteria(filterNodes));
    }

    if (safeRequest.sortBy() != null && !safeRequest.sortBy().isBlank()) {
      Sort.Direction dir = resolveSortDirection(safeRequest.sortDirection());
      query.with(Sort.by(dir, safeRequest.sortBy().trim()));
    }

    query.with(PageRequest.of(page, size));

    List<Map> results = mongoTemplate.find(query, Map.class, entity);
    List<FormRecordDto> content =
        results.stream()
            .map(
                result -> {
                  String id = result.get("_id") != null ? result.get("_id").toString() : null;
                  @SuppressWarnings("unchecked")
                  Map<String, Object> data = new HashMap<>((Map<String, Object>) result);
                  data.remove("_id");
                  data.remove("_class");
                  return new FormRecordDto(id, data);
                })
            .toList();

    long total = mongoTemplate.count(query.skip(-1).limit(-1), entity);

    return new PaginatedResponse<>(
        page, size, total, content, safeRequest.sortBy(), safeRequest.sortDirection());
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
      throw new IllegalArgumentException(
          "Size exceeds max page size: " + guardrailProperties.getMaxPageSize());
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
    SchemaVersion publishedSchema = schemaLifecycleService.latestPublished(entity);
    List<FieldDefinition> definitions = publishedSchema.getFields();

    if (definitions == null || definitions.isEmpty()) {
      throw new IllegalArgumentException("Published schema has no fields for entity: " + entity);
    }

    Map<String, FieldType> fieldTypeByPath = new HashMap<>();
    for (FieldDefinition definition : definitions) {
      registerFieldPaths(definition, "", fieldTypeByPath);
    }
    return fieldTypeByPath;
  }

  private void registerFieldPaths(
      FieldDefinition definition, String parentPath, Map<String, FieldType> fieldTypeByPath) {
    String path =
        parentPath.isEmpty()
            ? definition.getFieldName()
            : parentPath + "." + definition.getFieldName();

    fieldTypeByPath.put(path, definition.getType());
    if ((definition.getType() == FieldType.OBJECT || definition.getType() == FieldType.ARRAY)
        && definition.getSubFields() != null
        && !definition.getSubFields().isEmpty()) {
      for (FieldDefinition subField : definition.getSubFields()) {
        registerFieldPaths(subField, path, fieldTypeByPath);
      }
    }
  }

  private void validateSort(
      String sortBy, String sortDirection, Map<String, FieldType> fieldTypes) {
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

  private void validateFilters(List<FilterNode> filters, Map<String, FieldType> fieldTypes) {
    AtomicInteger ruleCount = new AtomicInteger();
    for (FilterNode filter : filters) {
      validateNode(filter, 1, ruleCount, fieldTypes);
    }
  }

  private void validateNode(
      FilterNode rule, int depth, AtomicInteger ruleCount, Map<String, FieldType> fieldTypes) {
    if (depth > guardrailProperties.getMaxFilterDepth()) {
      throw new IllegalArgumentException(
          "Filter depth exceeds max: " + guardrailProperties.getMaxFilterDepth());
    }
    if (ruleCount.incrementAndGet() > guardrailProperties.getMaxRuleCount()) {
      throw new IllegalArgumentException(
          "Filter rule count exceeds max: " + guardrailProperties.getMaxRuleCount());
    }

    switch (rule) {
      case FilterGroupNode groupNode -> {
        if (groupNode.rules().isEmpty()) {
          throw new IllegalArgumentException(
              "Combinator operator requires nested rules: " + groupNode.operator());
        }
        if ("not".equals(groupNode.operator()) && groupNode.rules().size() != 1) {
          throw new IllegalArgumentException("NOT operator requires exactly one nested rule");
        }
        for (FilterNode nestedRule : groupNode.rules()) {
          validateNode(nestedRule, depth + 1, ruleCount, fieldTypes);
        }
      }
      case FilterLeafNode leafNode -> {
        if (leafNode.field() == null) {
          throw new IllegalArgumentException("Filter field is required");
        }

        FieldType fieldType = fieldTypes.get(leafNode.field());
        if (fieldType == null) {
          throw new IllegalArgumentException(
              "Filtering by field is not allowed: " + leafNode.field());
        }

        validateOperatorForType(leafNode.field(), leafNode.operator(), fieldType);
        validateOperatorValue(leafNode.field(), leafNode.operator(), fieldType, leafNode.value());
      }
    }
  }

  private void validateOperatorForType(String field, String operator, FieldType fieldType) {
    Set<String> allowedOperators = allowedOperatorsFor(fieldType);
    if (!allowedOperators.contains(operator)) {
      throw new IllegalArgumentException(
          "Operator '"
              + operator
              + "' is not allowed for field '"
              + field
              + "' of type "
              + fieldType);
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

  private void validateOperatorValue(
      String field, String operator, FieldType fieldType, Object value) {
    if ("in".equals(operator)) {
      if (!(value instanceof Collection<?> collection)) {
        throw new IllegalArgumentException("IN operator requires a list for field: " + field);
      }
      validateCollectionValues(field, fieldType, collection);
      return;
    }

    if ("regex".equals(operator)) {
      if (!(value instanceof String)) {
        throw new IllegalArgumentException(
            "Regex operator requires string value for field: " + field);
      }
      return;
    }

    if (fieldType == FieldType.NUMBER
        && ("gt".equals(operator)
            || "lt".equals(operator)
            || "gte".equals(operator)
            || "lte".equals(operator))) {
      if (!(value instanceof Number)) {
        throw new IllegalArgumentException(
            "Operator '" + operator + "' requires numeric value for field: " + field);
      }
    }

    if (fieldType == FieldType.DATE
        && ("gt".equals(operator)
            || "lt".equals(operator)
            || "gte".equals(operator)
            || "lte".equals(operator))) {
      if (!(value instanceof String)) {
        throw new IllegalArgumentException(
            "Operator '" + operator + "' requires date-string value for field: " + field);
      }
    }
  }

  private void validateCollectionValues(
      String field, FieldType fieldType, Collection<?> collection) {
    for (Object item : collection) {
      switch (fieldType) {
        case NUMBER -> {
          if (!(item instanceof Number)) {
            throw new IllegalArgumentException(
                "IN operator requires numeric list for field: " + field);
          }
        }
        case BOOLEAN -> {
          if (!(item instanceof Boolean)) {
            throw new IllegalArgumentException(
                "IN operator requires boolean list for field: " + field);
          }
        }
        case DATE, STRING -> {
          if (!(item instanceof String)) {
            throw new IllegalArgumentException(
                "IN operator requires string list for field: " + field);
          }
        }
        case OBJECT, ARRAY ->
            throw new IllegalArgumentException("IN operator is not allowed for field: " + field);
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

  private Criteria buildCriteria(List<FilterNode> rules) {
    if (rules == null || rules.isEmpty()) {
      return new Criteria();
    }
    List<Criteria> criteriaList = rules.stream().map(this::buildCriteria).toList();
    if (criteriaList.size() == 1) {
      return criteriaList.get(0);
    }
    return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
  }

  private Criteria buildCriteria(FilterNode rule) {
    return switch (rule) {
      case FilterGroupNode groupNode -> {
        List<Criteria> nested = groupNode.rules().stream().map(this::buildCriteria).toList();
        yield switch (groupNode.operator()) {
          case "and" -> new Criteria().andOperator(nested.toArray(new Criteria[0]));
          case "or" -> new Criteria().orOperator(nested.toArray(new Criteria[0]));
          case "not" -> new Criteria().norOperator(nested.get(0));
          default ->
              throw new IllegalArgumentException(
                  "Unsupported combinator operator: " + groupNode.operator());
        };
      }
      case FilterLeafNode leafNode -> {
        yield switch (leafNode.operator()) {
          case "eq" -> Criteria.where(leafNode.field()).is(leafNode.value());
          case "ne" -> Criteria.where(leafNode.field()).ne(leafNode.value());
          case "gt" -> Criteria.where(leafNode.field()).gt(leafNode.value());
          case "lt" -> Criteria.where(leafNode.field()).lt(leafNode.value());
          case "gte" -> Criteria.where(leafNode.field()).gte(leafNode.value());
          case "lte" -> Criteria.where(leafNode.field()).lte(leafNode.value());
          case "in" -> Criteria.where(leafNode.field()).in((Collection<?>) leafNode.value());
          case "regex" -> Criteria.where(leafNode.field()).regex((String) leafNode.value());
          default -> Criteria.where(leafNode.field()).is(leafNode.value());
        };
      }
    };
  }

  private List<FilterNode> toFilterNodes(List<FilterRule> filters) {
    if (filters == null || filters.isEmpty()) {
      return List.of();
    }
    return filters.stream().map(this::toFilterNode).toList();
  }

  private FilterNode toFilterNode(FilterRule rule) {
    if (rule == null) {
      throw new IllegalArgumentException("Filter rule cannot be null");
    }

    String operator = normalizeOperator(rule.operator());
    if (COMBINATOR_OPERATORS.contains(operator)) {
      List<FilterRule> rawRules = rule.rules();
      List<FilterNode> nodes =
          rawRules == null ? List.of() : rawRules.stream().map(this::toFilterNode).toList();
      return new FilterGroupNode(operator, nodes);
    }

    if (rule.rules() != null && !rule.rules().isEmpty()) {
      throw new IllegalArgumentException("Leaf filter operators cannot include nested rules");
    }

    return new FilterLeafNode(normalizeField(rule.field()), operator, rule.value());
  }

  private sealed interface FilterNode permits FilterLeafNode, FilterGroupNode {}

  private record FilterLeafNode(String field, String operator, Object value)
      implements FilterNode {}

  private record FilterGroupNode(String operator, List<FilterNode> rules) implements FilterNode {
    private FilterGroupNode {
      rules = rules == null ? List.of() : List.copyOf(rules);
    }
  }
}
