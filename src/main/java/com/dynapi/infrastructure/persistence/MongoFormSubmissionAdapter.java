package com.dynapi.infrastructure.persistence;

import com.dynapi.application.port.output.FormSubmissionPersistencePort;
import com.dynapi.domain.model.FormSubmission;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class MongoFormSubmissionAdapter implements FormSubmissionPersistencePort {
    private final MongoTemplate mongoTemplate;

    public MongoFormSubmissionAdapter(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void save(FormSubmission submission) {
        mongoTemplate.save(submission.getData(), submission.getEntity());
    }
}
