package com.dynapi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dynapi.DynapiApplication;
import com.dynapi.controller.FormController;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.exception.GlobalExceptionHandler;
import com.dynapi.service.FormSubmissionService;
import java.util.Locale;
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
      FormControllerIntegrationTest.FormControllerTestConfig.class
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FormControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private FormSubmissionService formSubmissionService;

  @Test
  void submitForm_returnsSuccessEnvelope() throws Exception {
    String requestBody =
        """
        {
          "group": "profile",
          "data": {
            "name": "Alice"
          }
        }
        """;

    mockMvc
        .perform(
            post("/api/form")
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

    String requestBody =
        """
        {
          "group": "unknown",
          "data": {
            "name": "Alice"
          }
        }
        """;

    mockMvc
        .perform(
            post("/api/form")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.detail").value("Group not found"))
        .andExpect(jsonPath("$.title").value("Invalid Request"));
  }

  @Test
  void submitForm_returnsBadRequestWhenRequestValidationFails() throws Exception {
    String requestBody =
        """
        {
          "group": " ",
          "data": {
            "name": "Alice"
          }
        }
        """;

    mockMvc
        .perform(
            post("/api/form")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Validation Error"))
        .andExpect(jsonPath("$.detail").value("Validation failed"))
        .andExpect(jsonPath("$.errors.group").exists());

    verifyNoInteractions(formSubmissionService);
  }

  @Test
  void submitFormByGroup_returnsSuccessEnvelope() throws Exception {
    String requestBody =
        """
        {
          "name": "Alice"
        }
        """;

    mockMvc
        .perform(
            post("/api/forms/profile/submit")
                .contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("Form submitted successfully"));

    var requestCaptor = org.mockito.ArgumentCaptor.forClass(FormSubmissionRequest.class);
    verify(formSubmissionService).submitForm(requestCaptor.capture(), any(Locale.class));
    assertThat(requestCaptor.getValue().group()).isEqualTo("profile");
    assertThat(requestCaptor.getValue().data()).containsEntry("name", "Alice");
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
