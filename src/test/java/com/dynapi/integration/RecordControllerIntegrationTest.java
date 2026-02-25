package com.dynapi.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dynapi.DynapiApplication;
import com.dynapi.controller.RecordController;
import com.dynapi.domain.exception.EntityNotFoundException;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.RecordMutationRequest;
import com.dynapi.exception.GlobalExceptionHandler;
import com.dynapi.service.DynamicRecordService;

import java.util.Locale;
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
                RecordControllerIntegrationTest.RecordControllerTestConfig.class
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecordControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DynamicRecordService dynamicRecordService;

    @Test
    void patchRecord_returnsSuccessEnvelope() throws Exception {
        when(dynamicRecordService.patch(eq("tasks"), eq("record-1"), any(RecordMutationRequest.class), any(Locale.class)))
                .thenReturn(new FormRecordDto("record-1", Map.of("name", "Alice")));

        String requestBody =
                """
                        {
                          "data": {
                            "name": "Alice"
                          }
                        }
                        """;

        mockMvc
                .perform(
                        patch("/api/records/tasks/record-1")
                                .contextPath("/api")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record patched successfully"))
                .andExpect(jsonPath("$.data.id").value("record-1"))
                .andExpect(jsonPath("$.data.data.name").value("Alice"));

        verify(dynamicRecordService)
                .patch(eq("tasks"), eq("record-1"), any(RecordMutationRequest.class), any(Locale.class));
    }

    @Test
    void replaceRecord_returnsSuccessEnvelope() throws Exception {
        when(dynamicRecordService.replace(
                eq("tasks"), eq("record-1"), any(RecordMutationRequest.class), any(Locale.class)))
                .thenReturn(new FormRecordDto("record-1", Map.of("name", "Bob")));

        String requestBody =
                """
                        {
                          "data": {
                            "name": "Bob"
                          }
                        }
                        """;

        mockMvc
                .perform(
                        put("/api/records/tasks/record-1")
                                .contextPath("/api")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record replaced successfully"))
                .andExpect(jsonPath("$.data.id").value("record-1"))
                .andExpect(jsonPath("$.data.data.name").value("Bob"));

        verify(dynamicRecordService)
                .replace(eq("tasks"), eq("record-1"), any(RecordMutationRequest.class), any(Locale.class));
    }

    @Test
    void deleteRecord_returnsSuccessEnvelope() throws Exception {
        mockMvc
                .perform(delete("/api/records/tasks/record-1").contextPath("/api"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Record deleted successfully"));

        verify(dynamicRecordService).softDelete("tasks", "record-1");
    }

    @Test
    void patchRecord_returnsNotFoundWhenServiceThrowsEntityNotFound() throws Exception {
        doThrow(new EntityNotFoundException("Record not found"))
                .when(dynamicRecordService)
                .patch(eq("tasks"), eq("missing-id"), any(RecordMutationRequest.class), any(Locale.class));

        String requestBody =
                """
                        {
                          "data": {
                            "name": "Alice"
                          }
                        }
                        """;

        mockMvc
                .perform(
                        patch("/api/records/tasks/missing-id")
                                .contextPath("/api")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Entity Not Found"))
                .andExpect(jsonPath("$.detail").value("Record not found"));
    }

    @Test
    void patchRecord_returnsBadRequestWhenRequestValidationFails() throws Exception {
        String requestBody =
                """
                        {
                        }
                        """;

        mockMvc
                .perform(
                        patch("/api/records/tasks/record-1")
                                .contextPath("/api")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.errors.data").exists());

        verifyNoInteractions(dynamicRecordService);
    }

    @TestConfiguration
    static class RecordControllerTestConfig {
        @Bean
        RecordController recordController(DynamicRecordService dynamicRecordService) {
            return new RecordController(dynamicRecordService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler(MessageSource messageSource) {
            return new GlobalExceptionHandler(messageSource);
        }
    }
}
