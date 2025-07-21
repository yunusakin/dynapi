package com.dynapi.controller;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.service.FormSubmissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/form")
public class FormController {
    @Autowired
    private FormSubmissionService formSubmissionService;

    @PostMapping
    public ApiResponse<Void> submitForm(@RequestBody FormSubmissionRequest request) {
        formSubmissionService.submitForm(request, LocaleContextHolder.getLocale());
        return new ApiResponse<>(true, "Form submitted successfully", null);
    }
}
