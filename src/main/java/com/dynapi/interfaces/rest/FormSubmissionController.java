package com.dynapi.interfaces.rest;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.service.FormSubmissionService;
import io.swagger.v3.oas.annotations.Operation;

import java.util.Locale;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/forms", version = "1")
@RequiredArgsConstructor
public class FormSubmissionController {
    private final FormSubmissionService formSubmissionService;

    @PostMapping("/{groupId}/submit")
    @Operation(
            summary = "Submit Dynamic Form By Path Group",
            description = "Alternative submit endpoint where schema group is provided in path.")
    public ApiResponse<Void> submitForm(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> formData,
            @RequestHeader(name = "Accept-Language", required = false) Locale locale) {
        FormSubmissionRequest request = new FormSubmissionRequest(groupId, formData);
        Locale effectiveLocale = locale == null ? LocaleContextHolder.getLocale() : locale;
        formSubmissionService.submitForm(request, effectiveLocale);
        return ApiResponse.success(null, "Form submitted successfully");
    }
}
