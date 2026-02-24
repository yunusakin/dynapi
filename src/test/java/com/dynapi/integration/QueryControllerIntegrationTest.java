package com.dynapi.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dynapi.DynapiApplication;
import com.dynapi.controller.QueryController;
import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.exception.GlobalExceptionHandler;
import com.dynapi.service.DynamicQueryService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    classes = {
      DynapiApplication.class,
      QueryControllerIntegrationTest.QueryControllerTestConfig.class
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueryControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DynamicQueryService dynamicQueryService;

  @Test
  void query_returnsPaginatedResponseEnvelope_withDefaultApiVersionFallback() throws Exception {
    FormRecordDto record = new FormRecordDto("record-1", Map.of("name", "Alice"));
    PaginatedResponse<FormRecordDto> result =
        new PaginatedResponse<>(0, 10, 1L, List.of(record), "name", "ASC");

    when(dynamicQueryService.query(eq("customers"), any(DynamicQueryRequest.class)))
        .thenReturn(result);

    String requestBody =
        """
        {
          "page": 0,
          "size": 10,
          "sortBy": "name",
          "sortDirection": "ASC",
          "filters": [
            { "field": "name", "operator": "eq", "value": "Alice" }
          ]
        }
        """;

    mockMvc
        .perform(
            post("/api/query/customers")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Query successful"))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.size").value(10))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.data.content[0].id").value("record-1"))
        .andExpect(jsonPath("$.data.content[0].data.name").value("Alice"));

    verify(dynamicQueryService).query(eq("customers"), any(DynamicQueryRequest.class));
  }

  @Test
  void query_returnsPaginatedResponseEnvelope_withExplicitApiVersion() throws Exception {
    FormRecordDto record = new FormRecordDto("record-1", Map.of("name", "Alice"));
    PaginatedResponse<FormRecordDto> result =
        new PaginatedResponse<>(0, 10, 1L, List.of(record), "name", "ASC");

    when(dynamicQueryService.query(eq("customers"), any(DynamicQueryRequest.class)))
        .thenReturn(result);

    String requestBody =
        """
        {
          "page": 0,
          "size": 10,
          "sortBy": "name",
          "sortDirection": "ASC",
          "filters": [
            { "field": "name", "operator": "eq", "value": "Alice" }
          ]
        }
        """;

    mockMvc
        .perform(
            post("/api/query/customers?api-version=1")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Query successful"))
        .andExpect(jsonPath("$.data.page").value(0));

    verify(dynamicQueryService).query(eq("customers"), any(DynamicQueryRequest.class));
  }

  @Test
  void query_returnsBadRequestWhenRequestValidationFails() throws Exception {
    String requestBody =
        """
        {
          "page": -1,
          "size": 10
        }
        """;

    mockMvc
        .perform(
            post("/api/query/customers")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.detail").value("Validation failed"))
        .andExpect(jsonPath("$.errors.page").exists());

    verifyNoInteractions(dynamicQueryService);
  }

  @Test
  void query_returnsBadRequestWhenApiVersionUnsupported() throws Exception {
    String requestBody =
        """
        {
          "page": 0,
          "size": 10
        }
        """;

    mockMvc
        .perform(
            post("/api/query/customers?api-version=2")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").exists());

    verifyNoInteractions(dynamicQueryService);
  }

  @TestConfiguration
  static class QueryControllerTestConfig {
    @Bean
    QueryController queryController(DynamicQueryService dynamicQueryService) {
      return new QueryController(dynamicQueryService);
    }

    @Bean
    GlobalExceptionHandler globalExceptionHandler(MessageSource messageSource) {
      return new GlobalExceptionHandler(messageSource);
    }
  }
}
