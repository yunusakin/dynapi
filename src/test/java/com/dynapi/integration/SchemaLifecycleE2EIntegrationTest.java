package com.dynapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dynapi.domain.event.DomainEvent;
import com.dynapi.infrastructure.messaging.EventPublisher;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.JsonPathExpectationsHelper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchemaLifecycleE2EIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private MongoTemplate mongoTemplate;

    @MockitoSpyBean
    private EventPublisher eventPublisher;

    @BeforeEach
    void resetState() {
        mongoTemplate.getDb().drop();
        clearInvocations(eventPublisher);
    }

    @Test
    void publishSubmitQueryRecordMutationDeprecateFlow_usesPublishedSchemaAndEmitsLifecycleEvents()
            throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String fieldName = "e2e_name_" + suffix;
        String groupName = "e2e_group_" + suffix;
        String entityName = "e2e_entity_" + suffix;
        String adminToken = issueAdminToken();

        mockMvc
                .perform(
                        post("/api/admin/schema/field-definitions")
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "fieldName": "%s",
                                                  "type": "STRING",
                                                  "required": true,
                                                  "version": 1
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        post("/api/admin/schema/field-groups")
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "name": "%s",
                                                  "entity": "%s",
                                                  "fieldNames": ["%s"],
                                                  "version": 1
                                                }
                                                """
                                                .formatted(groupName, entityName, fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        post("/api/admin/schema/field-groups/%s/publish".formatted(groupName))
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc
                .perform(
                        post("/api/form")
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "group": "%s",
                                                  "data": {
                                                    "%s": "Alice"
                                                  }
                                                }
                                                """
                                                .formatted(groupName, fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        put("/api/admin/schema/field-definitions/%s".formatted(fieldName))
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "type": "NUMBER",
                                                  "required": true
                                                }
                                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        post("/api/form")
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "group": "%s",
                                                  "data": {
                                                    "%s": "Bob"
                                                  }
                                                }
                                                """
                                                .formatted(groupName, fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        post("/api/query/%s".formatted(entityName))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "filters": [],
                                                  "page": 0,
                                                  "size": 10
                                                }
                                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2));

        MvcResult queryBeforeMutation =
                mockMvc
                        .perform(
                                post("/api/query/%s".formatted(entityName))
                                        .contextPath("/api")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                        {
                                                          "filters": [],
                                                          "page": 0,
                                                          "size": 10
                                                        }
                                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.totalElements").value(2))
                        .andReturn();

        String recordId =
                new JsonPathExpectationsHelper("$.data.content[0].id")
                        .evaluateJsonPath(queryBeforeMutation.getResponse().getContentAsString(), String.class);

        mockMvc
                .perform(
                        patch("/api/records/%s/%s".formatted(entityName, recordId))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "data": {
                                                    "%s": "Patched"
                                                  }
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record patched successfully"))
                .andExpect(jsonPath("$.data.id").value(recordId))
                .andExpect(jsonPath("$.data.data.%s".formatted(fieldName)).value("Patched"));

        mockMvc
                .perform(
                        post("/api/query/%s".formatted(entityName))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "filters": [
                                                    { "field": "%s", "operator": "eq", "value": "Patched" }
                                                  ],
                                                  "page": 0,
                                                  "size": 10
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc
                .perform(
                        put("/api/records/%s/%s".formatted(entityName, recordId))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "data": {
                                                    "%s": "Replaced"
                                                  }
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record replaced successfully"))
                .andExpect(jsonPath("$.data.id").value(recordId))
                .andExpect(jsonPath("$.data.data.%s".formatted(fieldName)).value("Replaced"));

        mockMvc
                .perform(
                        post("/api/query/%s".formatted(entityName))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "filters": [
                                                    { "field": "%s", "operator": "eq", "value": "Replaced" }
                                                  ],
                                                  "page": 0,
                                                  "size": 10
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc
                .perform(delete("/api/records/%s/%s".formatted(entityName, recordId)).contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record deleted successfully"));

        mockMvc
                .perform(
                        post("/api/query/%s".formatted(entityName))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "filters": [
                                                    { "field": "%s", "operator": "eq", "value": "Replaced" }
                                                  ],
                                                  "page": 0,
                                                  "size": 10
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc
                .perform(
                        post("/api/query/%s".formatted(entityName))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "filters": [],
                                                  "page": 0,
                                                  "size": 10
                                                }
                                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc
                .perform(
                        patch("/api/records/%s/%s".formatted(entityName, recordId))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "data": {
                                                    "%s": "ShouldFail"
                                                  }
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Entity Not Found"))
                .andExpect(
                        jsonPath(
                                "$.detail",
                                Matchers.containsString(
                                        "Record not found for entity '" + entityName + "' and id '" + recordId + "'")));

        mockMvc
                .perform(
                        post("/api/admin/schema/entities/%s/deprecate".formatted(entityName))
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DEPRECATED"));

        mockMvc
                .perform(
                        post("/api/form")
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "group": "%s",
                                                  "data": {
                                                    "%s": "Charlie"
                                                  }
                                                }
                                                """
                                                .formatted(groupName, fieldName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"))
                .andExpect(
                        jsonPath(
                                "$.detail",
                                Matchers.containsString("No published schema found for entity: " + entityName)));

        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeast(2)).publishSchemaChange(eventCaptor.capture());

        List<String> lifecycleEventTypes =
                eventCaptor.getAllValues().stream()
                        .filter(event -> entityName.equals(event.getEntityName()))
                        .map(DomainEvent::getEventType)
                        .toList();

        assertThat(lifecycleEventTypes).contains("SCHEMA_PUBLISHED", "SCHEMA_DEPRECATED");
    }

    @Test
    void publishFlow_enforcesUniqueFieldOnSubmitAndPatch() throws Exception {
        String suffix = String.valueOf(System.currentTimeMillis());
        String fieldName = "e2e_unique_" + suffix;
        String groupName = "e2e_unique_group_" + suffix;
        String entityName = "e2e_unique_entity_" + suffix;
        String adminToken = issueAdminToken();

        mockMvc
                .perform(
                        post("/api/admin/schema/field-definitions")
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "fieldName": "%s",
                                                  "type": "STRING",
                                                  "required": true,
                                                  "unique": true,
                                                  "version": 1
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        post("/api/admin/schema/field-groups")
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "name": "%s",
                                                  "entity": "%s",
                                                  "fieldNames": ["%s"],
                                                  "version": 1
                                                }
                                                """
                                                .formatted(groupName, entityName, fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        post("/api/admin/schema/field-groups/%s/publish".formatted(groupName))
                                .contextPath("/api")
                                .header(AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc
                .perform(
                        post("/api/form")
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "group": "%s",
                                                  "data": {
                                                    "%s": "alpha"
                                                  }
                                                }
                                                """
                                                .formatted(groupName, fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc
                .perform(
                        post("/api/form")
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "group": "%s",
                                                  "data": {
                                                    "%s": "alpha"
                                                  }
                                                }
                                                """
                                                .formatted(groupName, fieldName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"))
                .andExpect(
                        jsonPath(
                                "$.detail",
                                Matchers.containsString(
                                        "Unique field violation for '" + fieldName + "' with value 'alpha'")));

        mockMvc
                .perform(
                        post("/api/form")
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "group": "%s",
                                                  "data": {
                                                    "%s": "beta"
                                                  }
                                                }
                                                """
                                                .formatted(groupName, fieldName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        MvcResult betaRecordResult =
                mockMvc
                        .perform(
                                post("/api/query/%s".formatted(entityName))
                                        .contextPath("/api")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                        {
                                                          "filters": [
                                                            { "field": "%s", "operator": "eq", "value": "beta" }
                                                          ],
                                                          "page": 0,
                                                          "size": 10
                                                        }
                                                        """
                                                        .formatted(fieldName)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.totalElements").value(1))
                        .andReturn();

        String betaRecordId =
                new JsonPathExpectationsHelper("$.data.content[0].id")
                        .evaluateJsonPath(betaRecordResult.getResponse().getContentAsString(), String.class);

        mockMvc
                .perform(
                        patch("/api/records/%s/%s".formatted(entityName, betaRecordId))
                                .contextPath("/api")
                                .contentType(APPLICATION_JSON)
                                .content(
                                        """
                                                {
                                                  "data": {
                                                    "%s": "alpha"
                                                  }
                                                }
                                                """
                                                .formatted(fieldName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Request"))
                .andExpect(
                        jsonPath(
                                "$.detail",
                                Matchers.containsString(
                                        "Unique field violation for '" + fieldName + "' with value 'alpha'")));
    }

    private String issueAdminToken() throws Exception {
        MvcResult result =
                mockMvc
                        .perform(
                                post("/api/dev/auth/token")
                                        .contextPath("/api")
                                        .contentType(APPLICATION_JSON)
                                        .content(
                                                """
                                                        {
                                                          "subject": "e2e-admin",
                                                          "roles": ["ADMIN"],
                                                          "ttlSeconds": 3600
                                                        }
                                                        """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.success").value(true))
                        .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                        .andReturn();

        return new JsonPathExpectationsHelper("$.data.token")
                .evaluateJsonPath(result.getResponse().getContentAsString(), String.class);
    }
}
