package com.dynapi.repository;

import com.dynapi.domain.model.FieldGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FieldGroupRepository extends MongoRepository<FieldGroup, String> {
    Optional<FieldGroup> findByEntity(String entity);
}
