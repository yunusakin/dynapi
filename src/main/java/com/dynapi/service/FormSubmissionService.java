package com.dynapi.service;

import com.dynapi.dto.FormSubmissionRequest;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.repository.FieldGroupRepository;
import java.util.List;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.validation.Validator;
import org.springframework.data.mongodb.core.MongoTemplate;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FormSubmissionService {
    private final com.dynapi.audit.AuditPublisher auditPublisher;
    private final com.dynapi.repository.FieldGroupRepository fieldGroupRepository;
    private final MongoTemplate mongoTemplate;
    private final MessageSource messageSource;
    private final com.dynapi.repository.FieldDefinitionRepository fieldDefinitionRepository;
    private final com.dynapi.domain.validation.DynamicValidator dynamicValidator;

    public void submitForm(FormSubmissionRequest request, Locale locale) {
        // 1. Load schema using group
        Optional<FieldGroup> groupOpt = fieldGroupRepository.findById(request.getGroup());
        if (groupOpt.isEmpty()) {
            throw new IllegalArgumentException(messageSource.getMessage("error.group.notfound", null, locale));
        }
        FieldGroup group = groupOpt.get();
        // 2. Load field definitions for this group
        List<FieldDefinition> schema = fieldDefinitionRepository.findAllById(group.getFieldNames());
        // 3. Validate input recursively and type-safe
        dynamicValidator.validate(request.getData(), schema, locale);
        // 4. Save form data to collection by entity
        String collectionName = group.getEntity();
        mongoTemplate.save(request.getData(), collectionName);
        // 5. Audit event
        auditPublisher.publish("FORM_SUBMIT", collectionName, request.getData());
    }
}
