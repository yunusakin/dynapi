package com.dynapi.application.port;

import com.dynapi.domain.model.FormSubmission;

public interface FormSubmissionPersistencePort {
    void save(FormSubmission submission);
    // Define other persistence methods here
}
