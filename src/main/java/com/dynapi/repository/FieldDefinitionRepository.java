package com.dynapi.repository;

import com.dynapi.domain.model.FieldDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldDefinitionRepository extends MongoRepository<FieldDefinition, String> {
}
