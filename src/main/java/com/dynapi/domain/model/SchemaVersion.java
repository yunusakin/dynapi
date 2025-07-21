package com.dynapi.domain.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SchemaVersion {
    private String id;
    private String entityName;
    private Integer version;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private List<FieldDefinition> fields;
    private String status; // DRAFT, ACTIVE, DEPRECATED
    private String createdBy;
    private LocalDateTime createdAt;
    private String modifiedBy;
    private LocalDateTime modifiedAt;
}
