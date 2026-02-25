package com.dynapi.repository;

import com.dynapi.domain.model.FieldGroup;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldGroupRepository extends MongoRepository<FieldGroup, String> {
    Optional<FieldGroup> findByEntity(String entity);

    Optional<FieldGroup> findTopByNameOrderByVersionDesc(String name);

    long deleteByName(String name);
}
