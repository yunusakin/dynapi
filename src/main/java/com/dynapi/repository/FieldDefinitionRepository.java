package com.dynapi.repository;

import com.dynapi.domain.model.FieldDefinition;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FieldDefinitionRepository extends MongoRepository<FieldDefinition, String> {
    List<FieldDefinition> findByFieldNameIn(Collection<String> fieldNames);
    Optional<FieldDefinition> findTopByFieldNameOrderByVersionDesc(String fieldName);
    long deleteByFieldName(String fieldName);
}
