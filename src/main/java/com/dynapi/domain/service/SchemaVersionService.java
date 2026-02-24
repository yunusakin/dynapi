package com.dynapi.domain.service;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.SchemaVersion;
import java.util.List;
import java.util.Map;

public interface SchemaVersionService {
  SchemaVersion createNewVersion(String entityName, List<FieldDefinition> fields);

  SchemaVersion getActiveVersion(String entityName);

  SchemaVersion getVersion(String entityName, Integer version);

  void activateVersion(String entityName, Integer version);

  void deprecateVersion(String entityName, Integer version);

  Map<String, Object> migrateData(Map<String, Object> data, Integer fromVersion, Integer toVersion);
}
