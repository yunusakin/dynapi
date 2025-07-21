package com.dynapi.domain.repository;

import com.dynapi.domain.model.FieldDefinition;
import java.util.Optional;
import java.util.List;

public interface FieldDefinitionRepository {
    FieldDefinition save(FieldDefinition fieldDefinition);
    Optional<FieldDefinition> findById(String id);
    List<FieldDefinition> findAll();
    void deleteById(String id);
    List<FieldDefinition> findByFieldGroupId(String fieldGroupId);
    boolean existsById(String id);
}
