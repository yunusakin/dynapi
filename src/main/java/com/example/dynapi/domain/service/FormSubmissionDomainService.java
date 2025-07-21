package com.example.dynapi.domain.service;

import com.example.dynapi.domain.model.FormSubmission;
import com.example.dynapi.domain.model.FieldGroup;
import com.example.dynapi.domain.model.FieldDefinition;
import java.util.List;
import java.util.Map;

public interface FormSubmissionDomainService {
    void validateSubmission(Map<String, Object> data, List<FieldDefinition> schema);
    FormSubmission createSubmission(String groupId, Map<String, Object> data, FieldGroup group);
}
