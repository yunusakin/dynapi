package com.dynapi.repository;

import com.dynapi.domain.model.SchemaLifecycleStatus;
import com.dynapi.domain.model.SchemaVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SchemaVersionRepository extends MongoRepository<SchemaVersion, String> {
  Optional<SchemaVersion> findTopByEntityNameAndStatusOrderByVersionDesc(
      String entityName, SchemaLifecycleStatus status);

  List<SchemaVersion> findByEntityNameOrderByVersionDesc(String entityName);

  Optional<SchemaVersion> findByEntityNameAndVersion(String entityName, Integer version);

  Optional<SchemaVersion> findTopByEntityNameOrderByVersionDesc(String entityName);
}
