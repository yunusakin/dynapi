package com.example.dynapi.domain.service;

import com.example.dynapi.domain.model.FormSubmission;
import com.example.dynapi.domain.model.FieldGroup;
import com.example.dynapi.domain.model.FieldDefinition;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FormSubmissionDomainServiceImpl implements FormSubmissionDomainService {
    private final DynamicValidator dynamicValidator;

    public FormSubmissionDomainServiceImpl(DynamicValidator dynamicValidator) {
        this.dynamicValidator = dynamicValidator;
    }

    @Override
    public void validateSubmission(Map<String, Object> data, List<FieldDefinition> schema) {
        dynamicValidator.validate(data, schema);
    }

    @Override
    public FormSubmission createSubmission(String groupId, Map<String, Object> data, FieldGroup group) {
        return new FormSubmission(groupId, data, group.getEntity());
    }
}
