package com.example.dynapi.application.service;

import com.example.dynapi.application.port.input.SubmitFormUseCase;
import com.example.dynapi.domain.model.FieldGroup;
import com.example.dynapi.domain.model.FieldDefinition;
import com.example.dynapi.domain.model.FormSubmission;
import com.example.dynapi.domain.repository.FieldGroupRepository;
import com.example.dynapi.domain.repository.FieldDefinitionRepository;
import com.example.dynapi.domain.service.FormSubmissionDomainService;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class FormSubmissionApplicationService implements SubmitFormUseCase {
    private final FieldGroupRepository fieldGroupRepository;
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final FormSubmissionDomainService formSubmissionDomainService;
    private final FormSubmissionPersistencePort formSubmissionPersistencePort;
    private final MessageSource messageSource;
    private final AuditPublisher auditPublisher;

    public FormSubmissionApplicationService(
            FieldGroupRepository fieldGroupRepository,
            FieldDefinitionRepository fieldDefinitionRepository,
            FormSubmissionDomainService formSubmissionDomainService,
            FormSubmissionPersistencePort formSubmissionPersistencePort,
            MessageSource messageSource,
            AuditPublisher auditPublisher) {
        this.fieldGroupRepository = fieldGroupRepository;
        this.fieldDefinitionRepository = fieldDefinitionRepository;
        this.formSubmissionDomainService = formSubmissionDomainService;
        this.formSubmissionPersistencePort = formSubmissionPersistencePort;
        this.messageSource = messageSource;
        this.auditPublisher = auditPublisher;
    }

    @Override
    public void submitForm(String groupId, Map<String, Object> formData, Locale locale) {
        // 1. Load schema using group
        FieldGroup group = fieldGroupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException(
                messageSource.getMessage("error.group.notfound", null, locale)));

        // 2. Load field definitions for this group
        List<FieldDefinition> schema = fieldDefinitionRepository.findByFieldGroupId(groupId);

        // 3. Validate input
        formSubmissionDomainService.validateSubmission(formData, schema);

        // 4. Create domain object
        FormSubmission submission = formSubmissionDomainService.createSubmission(groupId, formData, group);

        // 5. Save form submission
        formSubmissionPersistencePort.save(submission);

        // 6. Publish audit event
        auditPublisher.publish("FORM_SUBMIT", group.getEntity(), formData);
    }
}
