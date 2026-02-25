package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.config.QueryGuardrailProperties;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FilterRule;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class DynamicQueryServiceTest {

  @Mock private MongoTemplate mongoTemplate;

  @Mock private SchemaLifecycleService schemaLifecycleService;

  private DynamicQueryService dynamicQueryService;

  @BeforeEach
  void setUp() {
    QueryGuardrailProperties guardrails = new QueryGuardrailProperties();
    guardrails.setMaxPageSize(100);
    guardrails.setMaxFilterDepth(3);
    guardrails.setMaxRuleCount(20);

    dynamicQueryService =
        new DynamicQueryService(mongoTemplate, schemaLifecycleService, guardrails);

    FieldDefinition title = field("title", FieldType.STRING);
    FieldDefinition priority = field("priority", FieldType.NUMBER);
    FieldDefinition profile = field("profile", FieldType.OBJECT);
    FieldDefinition age = field("age", FieldType.NUMBER);
    profile.setSubFields(List.of(age));

    SchemaVersion published = new SchemaVersion();
    published.setEntityName("tasks");
    published.setVersion(1);
    published.setStatus(SchemaLifecycleStatus.PUBLISHED);
    published.setCreatedAt(LocalDateTime.now());
    published.setFields(List.of(title, priority, profile));

    lenient().when(schemaLifecycleService.latestPublished("tasks")).thenReturn(published);
    lenient().when(mongoTemplate.find(any(), eq(Map.class), eq("tasks"))).thenReturn(List.of());
    lenient().when(mongoTemplate.count(any(), eq("tasks"))).thenReturn(0L);
  }

  @Test
  void query_rejectsSizeAboveMax() {
    DynamicQueryRequest request = new DynamicQueryRequest(null, null, 101, null, null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("max page size"));
  }

  @Test
  void query_rejectsUnknownSortField() {
    DynamicQueryRequest request = new DynamicQueryRequest(null, null, null, "unknownField", "ASC");

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("Sorting by field is not allowed"));
  }

  @Test
  void query_rejectsUnknownFilterField() {
    DynamicQueryRequest request =
        new DynamicQueryRequest(List.of(filter("unknown", "eq", "x")), null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("Filtering by field is not allowed"));
  }

  @Test
  void query_rejectsUnsupportedOperatorForFieldType() {
    DynamicQueryRequest request =
        new DynamicQueryRequest(
            List.of(filter("priority", "regex", "^1$")), null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("not allowed"));
  }

  @Test
  void query_rejectsFilterDepthExceeded() {
    DynamicQueryRequest request =
        new DynamicQueryRequest(
            List.of(nestedAnd(4, filter("title", "eq", "A"))), null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("Filter depth exceeds max"));
  }

  @Test
  void query_rejectsFilterRuleCountExceeded() {
    List<FilterRule> tooManyRules = new ArrayList<>();
    for (int i = 0; i < 21; i++) {
      tooManyRules.add(filter("title", "eq", "task-" + i));
    }

    DynamicQueryRequest request = new DynamicQueryRequest(tooManyRules, null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("Filter rule count exceeds max"));
  }

  @Test
  void query_allowsNinOnNumericField() {
    DynamicQueryRequest request =
        new DynamicQueryRequest(List.of(filter("priority", "nin", List.of(1, 2))), 0, 10, null, null);

    PaginatedResponse<FormRecordDto> response = dynamicQueryService.query("tasks", request);

    assertEquals(0, response.page());
    assertEquals(10, response.size());
  }

  @Test
  void query_rejectsNinWhenValueIsNotCollection() {
    DynamicQueryRequest request =
        new DynamicQueryRequest(List.of(filter("priority", "nin", 2)), null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("NIN operator requires a list"));
  }

  @Test
  void query_allowsExistsWithBooleanValues() {
    DynamicQueryRequest existsTrueRequest =
        new DynamicQueryRequest(List.of(filter("profile", "exists", true)), 0, 10, null, null);
    DynamicQueryRequest existsFalseRequest =
        new DynamicQueryRequest(List.of(filter("profile.age", "exists", false)), 0, 10, null, null);

    PaginatedResponse<FormRecordDto> trueResponse =
        dynamicQueryService.query("tasks", existsTrueRequest);
    PaginatedResponse<FormRecordDto> falseResponse =
        dynamicQueryService.query("tasks", existsFalseRequest);

    assertEquals(0, trueResponse.page());
    assertEquals(0, falseResponse.page());
  }

  @Test
  void query_rejectsExistsWhenValueIsNotBoolean() {
    DynamicQueryRequest request =
        new DynamicQueryRequest(
            List.of(filter("profile", "exists", "true")), null, null, null, null);

    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class, () -> dynamicQueryService.query("tasks", request));

    assertTrue(ex.getMessage().contains("EXISTS operator requires boolean value"));
  }

  @Test
  void query_allowsUppercaseCombinators() {
    FilterRule notRule = new FilterRule(null, "NOT", null, List.of(filter("priority", "gt", 1)));
    FilterRule orRule =
        new FilterRule(
            null,
            "OR",
            null,
            List.of(filter("title", "eq", "A"), filter("title", "eq", "B")));
    FilterRule andRule = new FilterRule(null, "AND", null, List.of(orRule, notRule));

    DynamicQueryRequest request = new DynamicQueryRequest(List.of(andRule), 0, 10, null, null);

    PaginatedResponse<FormRecordDto> response = dynamicQueryService.query("tasks", request);

    assertEquals(0, response.page());
    assertEquals(10, response.size());
  }

  @Test
  void query_excludesSoftDeletedRecordsByDefault() {
    DynamicQueryRequest request = new DynamicQueryRequest(null, 0, 10, null, null);

    dynamicQueryService.query("tasks", request);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    verify(mongoTemplate).find(queryCaptor.capture(), eq(Map.class), eq("tasks"));
    String queryJson = queryCaptor.getValue().getQueryObject().toJson();
    assertTrue(queryJson.contains("\"deleted\""));
    assertTrue(queryJson.contains("\"$ne\""));
  }

  @Test
  void query_allowsValidGuardedQuery() {
    DynamicQueryRequest request =
        new DynamicQueryRequest(
            List.of(filter("priority", "gte", 1), filter("profile.age", "lt", 65)),
            0,
            10,
            "priority",
            "DESC");

    when(mongoTemplate.find(any(), eq(Map.class), eq("tasks")))
        .thenReturn(
            List.of(
                Map.of(
                    "_id", "id-1",
                    "title", "Task",
                    "priority", 2)));
    when(mongoTemplate.count(any(), eq("tasks"))).thenReturn(1L);

    PaginatedResponse<FormRecordDto> response = dynamicQueryService.query("tasks", request);

    assertEquals(0, response.page());
    assertEquals(10, response.size());
    assertEquals(1L, response.totalElements());
    assertEquals(1, response.content().size());
    assertEquals("id-1", response.content().get(0).id());
    assertEquals(2, response.content().get(0).data().get("priority"));

    verify(mongoTemplate).find(any(), eq(Map.class), eq("tasks"));
    verify(mongoTemplate).count(any(), eq("tasks"));
  }

  private FieldDefinition field(String name, FieldType type) {
    FieldDefinition definition = new FieldDefinition();
    definition.setFieldName(name);
    definition.setType(type);
    return definition;
  }

  private FilterRule filter(String field, String operator, Object value) {
    return new FilterRule(field, operator, value, null);
  }

  private FilterRule nestedAnd(int depth, FilterRule leaf) {
    if (depth <= 1) {
      return leaf;
    }
    return new FilterRule(null, "and", null, List.of(nestedAnd(depth - 1, leaf)));
  }
}
