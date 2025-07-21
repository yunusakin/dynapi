package com.dynapi.repository;

import com.dynapi.domain.model.FieldGroup;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FieldGroupRepository extends MongoRepository<FieldGroup, String> {
}
