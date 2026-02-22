package com.dynapi.interfaces.rest;

import com.dynapi.application.port.input.SubmitFormUseCase;
import com.dynapi.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;
import java.util.Map;

@RequestMapping("/forms")
public class FormSubmissionController {
    private final SubmitFormUseCase submitFormUseCase;

    public FormSubmissionController(SubmitFormUseCase submitFormUseCase) {
        this.submitFormUseCase = submitFormUseCase;
    }

    @PostMapping("/{groupId}/submit")
    public ApiResponse<Void> submitForm(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> formData,
            @RequestHeader(name = "Accept-Language", required = false) Locale locale) {
        submitFormUseCase.submitForm(groupId, formData, locale);
        return ApiResponse.success(null, "Form submitted successfully");
    }
}
