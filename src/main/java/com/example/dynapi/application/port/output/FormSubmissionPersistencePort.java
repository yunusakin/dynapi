package com.example.dynapi.application.port.output;

import com.example.dynapi.domain.model.FormSubmission;

public interface FormSubmissionPersistencePort {
    void save(FormSubmission submission);
}
