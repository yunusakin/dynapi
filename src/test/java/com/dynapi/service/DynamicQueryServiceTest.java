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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicQueryServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private FieldGroupRepository fieldGroupRepository;

    @Mock
    private FieldDefinitionRepository fieldDefinitionRepository;

    private DynamicQueryService dynamicQueryService;

    @BeforeEach
    void setUp() {
        QueryGuardrailProperties guardrails = new QueryGuardrailProperties();
        guardrails.setMaxPageSize(100);
        guardrails.setMaxFilterDepth(3);
        guardrails.setMaxRuleCount(20);

        dynamicQueryService = new DynamicQueryService(
                mongoTemplate,
                fieldGroupRepository,
                fieldDefinitionRepository,
                guardrails
        );

        FieldGroup group = new FieldGroup();
        group.setName("task-group");
        group.setEntity("tasks");
        group.setFieldNames(List.of("title", "priority", "profile"));
        lenient().when(fieldGroupRepository.findByEntity("tasks")).thenReturn(Optional.of(group));

        FieldDefinition title = field("title", FieldType.STRING);
        FieldDefinition priority = field("priority", FieldType.NUMBER);
        FieldDefinition profile = field("profile", FieldType.OBJECT);
        FieldDefinition age = field("age", FieldType.NUMBER);
        profile.setSubFields(List.of(age));
        lenient().when(fieldDefinitionRepository.findAllById(group.getFieldNames()))
                .thenReturn(List.of(title, priority, profile));
    }

    @Test
    void query_rejectsSizeAboveMax() {
        DynamicQueryRequest request = new DynamicQueryRequest();
        request.setSize(101);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dynamicQueryService.query("tasks", request)
        );

        assertTrue(ex.getMessage().contains("max page size"));
    }

    @Test
    void query_rejectsUnknownSortField() {
        DynamicQueryRequest request = new DynamicQueryRequest();
        request.setSortBy("unknownField");
        request.setSortDirection("ASC");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dynamicQueryService.query("tasks", request)
        );

        assertTrue(ex.getMessage().contains("Sorting by field is not allowed"));
    }

    @Test
    void query_rejectsUnknownFilterField() {
        DynamicQueryRequest request = new DynamicQueryRequest();
        request.setFilters(List.of(filter("unknown", "eq", "x")));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dynamicQueryService.query("tasks", request)
        );

        assertTrue(ex.getMessage().contains("Filtering by field is not allowed"));
    }

    @Test
    void query_rejectsUnsupportedOperatorForFieldType() {
        DynamicQueryRequest request = new DynamicQueryRequest();
        request.setFilters(List.of(filter("priority", "regex", "^1$")));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dynamicQueryService.query("tasks", request)
        );

        assertTrue(ex.getMessage().contains("not allowed"));
    }

    @Test
    void query_rejectsFilterDepthExceeded() {
        DynamicQueryRequest request = new DynamicQueryRequest();
        request.setFilters(List.of(nestedAnd(4, filter("title", "eq", "A"))));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dynamicQueryService.query("tasks", request)
        );

        assertTrue(ex.getMessage().contains("Filter depth exceeds max"));
    }

    @Test
    void query_rejectsFilterRuleCountExceeded() {
        List<FilterRule> tooManyRules = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            tooManyRules.add(filter("title", "eq", "task-" + i));
        }

        DynamicQueryRequest request = new DynamicQueryRequest();
        request.setFilters(tooManyRules);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> dynamicQueryService.query("tasks", request)
        );

        assertTrue(ex.getMessage().contains("Filter rule count exceeds max"));
    }

    @Test
    void query_allowsValidGuardedQuery() {
        DynamicQueryRequest request = new DynamicQueryRequest();
        request.setPage(0);
        request.setSize(10);
        request.setSortBy("priority");
        request.setSortDirection("DESC");
        request.setFilters(List.of(
                filter("priority", "gte", 1),
                filter("profile.age", "lt", 65)
        ));

        when(mongoTemplate.find(any(), eq(Map.class), eq("tasks")))
                .thenReturn(List.of(Map.of(
                        "_id", "id-1",
                        "title", "Task",
                        "priority", 2
                )));
        when(mongoTemplate.count(any(), eq("tasks"))).thenReturn(1L);

        PaginatedResponse<FormRecordDto> response = dynamicQueryService.query("tasks", request);

        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals(1L, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals("id-1", response.getContent().get(0).getId());
        assertEquals(2, response.getContent().get(0).getData().get("priority"));

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
        FilterRule rule = new FilterRule();
        rule.setField(field);
        rule.setOperator(operator);
        rule.setValue(value);
        return rule;
    }

    private FilterRule nestedAnd(int depth, FilterRule leaf) {
        if (depth <= 1) {
            return leaf;
        }
        FilterRule parent = new FilterRule();
        parent.setOperator("and");
        parent.setRules(List.of(nestedAnd(depth - 1, leaf)));
        return parent;
    }
}
