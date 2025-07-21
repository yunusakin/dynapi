package com.example.dynapi.service;

import com.example.dynapi.dto.FormSubmissionRequest;
import com.example.dynapi.domain.model.FieldGroup;
import com.example.dynapi.domain.model.FieldDefinition;
import com.example.dynapi.domain.repository.FieldGroupRepository;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Locale;
import java.util.Optional;

@Service
public class FormSubmissionService {
    @Autowired
    private com.example.dynapi.audit.AuditPublisher auditPublisher;
    @Autowired
    private com.example.dynapi.repository.FieldGroupRepository fieldGroupRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private MessageSource messageSource;
    @Autowired
    private Validator validator;

    @Autowired
    private com.example.dynapi.repository.FieldDefinitionRepository fieldDefinitionRepository;
    @Autowired
    private com.example.dynapi.validation.DynamicValidator dynamicValidator;

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
        dynamicValidator.validate(request.getData(), schema, messageSource, locale);
        // 4. Save form data to collection by entity
        String collectionName = group.getEntity();
        mongoTemplate.save(request.getData(), collectionName);
        // 5. Audit event
        auditPublisher.publish("FORM_SUBMIT", collectionName, request.getData());
    }
}
