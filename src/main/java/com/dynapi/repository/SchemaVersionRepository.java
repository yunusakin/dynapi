package com.dynapi.repository;

import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SchemaVersionRepository extends MongoRepository<SchemaVersion, String> {
    Optional<SchemaVersion> findTopByEntityNameAndStatusOrderByVersionDesc(String entityName, SchemaLifecycleStatus status);

    List<SchemaVersion> findByEntityNameOrderByVersionDesc(String entityName);

    Optional<SchemaVersion> findByEntityNameAndVersion(String entityName, Integer version);
}
