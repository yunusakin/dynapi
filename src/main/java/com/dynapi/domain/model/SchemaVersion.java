package com.dynapi.domain.model;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "schema_versions")
public class SchemaVersion {
    @Id
    private String id;
    private String entityName;
    private String groupName;
    private Integer version;
    private SchemaLifecycleStatus status;
    private List<FieldDefinition> fields;
    private LocalDateTime publishedAt;
    private LocalDateTime deprecatedAt;
    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;
}
