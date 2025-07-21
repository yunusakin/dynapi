package com.example.dynapi.application.port.input;

import com.example.dynapi.domain.model.FormSubmission;
import java.util.Map;
import java.util.Locale;

public interface SubmitFormUseCase {
    void submitForm(String groupId, Map<String, Object> formData, Locale locale);
}
