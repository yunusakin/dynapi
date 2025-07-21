package com.example.dynapi.repository;

import com.example.dynapi.model.FieldDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldDefinitionRepository extends MongoRepository<FieldDefinition, String> {
}
