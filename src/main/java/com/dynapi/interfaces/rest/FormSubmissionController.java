package com.dynapi.interfaces.rest;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.service.FormSubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/forms")
@RequiredArgsConstructor
public class FormSubmissionController {
    private final FormSubmissionService formSubmissionService;

    @PostMapping("/{groupId}/submit")
    public ApiResponse<Void> submitForm(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> formData,
            @RequestHeader(name = "Accept-Language", required = false) Locale locale) {
        FormSubmissionRequest request = new FormSubmissionRequest();
        request.setGroup(groupId);
        request.setData(formData);

        Locale effectiveLocale = locale == null ? LocaleContextHolder.getLocale() : locale;
        formSubmissionService.submitForm(request, effectiveLocale);
        return ApiResponse.success(null, "Form submitted successfully");
    }
}
