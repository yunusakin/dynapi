package com.dynapi.integration;

import com.dynapi.controller.QueryController;
import com.dynapi.DynapiApplication;
import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.exception.GlobalExceptionHandler;
import com.dynapi.service.DynamicQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {DynapiApplication.class, QueryControllerIntegrationTest.QueryControllerTestConfig.class}
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QueryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamicQueryService dynamicQueryService;

    @Test
    void query_returnsPaginatedResponseEnvelope() throws Exception {
        FormRecordDto record = new FormRecordDto();
        record.setId("record-1");
        record.setData(Map.of("name", "Alice"));

        PaginatedResponse<FormRecordDto> result = new PaginatedResponse<>();
        result.setPage(0);
        result.setSize(10);
        result.setTotalElements(1L);
        result.setSortBy("name");
        result.setSortDirection("ASC");
        result.setContent(List.of(record));

        when(dynamicQueryService.query(eq("customers"), any(DynamicQueryRequest.class)))
                .thenReturn(result);

        String requestBody = """
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

        mockMvc.perform(post("/api/query/customers")
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
