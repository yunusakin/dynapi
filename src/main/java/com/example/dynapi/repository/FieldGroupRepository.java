package com.example.dynapi.repository;

import com.example.dynapi.model.FieldGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldGroupRepository extends MongoRepository<FieldGroup, String> {
}
