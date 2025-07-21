package com.dynapi.application.port.output;

import com.dynapi.domain.model.FormSubmission;

public interface FormSubmissionPersistencePort {
    void save(FormSubmission submission);
}
