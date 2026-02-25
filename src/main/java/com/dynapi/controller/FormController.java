package com.dynapi.controller;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.service.FormSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/form", version = "1")
@RequiredArgsConstructor
public class FormController {
    private final FormSubmissionService formSubmissionService;

    @PostMapping
    @Operation(
            summary = "Submit Dynamic Form",
            description = "Submits dynamic form payload for a schema group.")
    public ApiResponse<Void> submitForm(@RequestBody @Valid FormSubmissionRequest request) {
        formSubmissionService.submitForm(request, LocaleContextHolder.getLocale());
        return ApiResponse.success(null, "Form submitted successfully");
    }
}
