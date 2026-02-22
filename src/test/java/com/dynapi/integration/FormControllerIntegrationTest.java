package com.dynapi.integration;

import com.dynapi.controller.FormController;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.DynapiApplication;
import com.dynapi.exception.GlobalExceptionHandler;
import com.dynapi.service.FormSubmissionService;
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

import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {DynapiApplication.class, FormControllerIntegrationTest.FormControllerTestConfig.class}
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FormSubmissionService formSubmissionService;

    @Test
    void submitForm_returnsSuccessEnvelope() throws Exception {
        String requestBody = """
                {
                  "group": "profile",
                  "data": {
                    "name": "Alice"
                  }
                }
                """;

        mockMvc.perform(post("/api/form")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Form submitted successfully"))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(formSubmissionService).submitForm(any(FormSubmissionRequest.class), any(Locale.class));
    }

    @Test
    void submitForm_returnsBadRequestWhenServiceThrowsIllegalArgument() throws Exception {
        doThrow(new IllegalArgumentException("Group not found"))
                .when(formSubmissionService)
                .submitForm(any(FormSubmissionRequest.class), any(Locale.class));

        String requestBody = """
                {
                  "group": "unknown",
                  "data": {
                    "name": "Alice"
                  }
                }
                """;

        mockMvc.perform(post("/api/form")
                        .contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Group not found"));
    }

    @TestConfiguration
    static class FormControllerTestConfig {
        @Bean
        FormController formController(FormSubmissionService formSubmissionService) {
            return new FormController(formSubmissionService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler(MessageSource messageSource) {
            return new GlobalExceptionHandler(messageSource);
        }
    }
}
