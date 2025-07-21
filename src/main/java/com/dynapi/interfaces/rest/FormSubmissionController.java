package com.dynapi.interfaces.rest;

import com.dynapi.application.port.input.SubmitFormUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/forms")
public class FormSubmissionController {
    private final SubmitFormUseCase submitFormUseCase;

    public FormSubmissionController(SubmitFormUseCase submitFormUseCase) {
        this.submitFormUseCase = submitFormUseCase;
    }

    @PostMapping("/{groupId}/submit")
    public ResponseEntity<Void> submitForm(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> formData,
            @RequestHeader(name = "Accept-Language", required = false) Locale locale) {
        submitFormUseCase.submitForm(groupId, formData, locale);
        return ResponseEntity.ok().build();
    }
}
