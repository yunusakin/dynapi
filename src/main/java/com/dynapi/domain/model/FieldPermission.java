package com.dynapi.domain.model;

import lombok.Data;
import java.util.Set;

@Data
public class FieldPermission {
    private String id;
    private String fieldName;
    private String entityName;
    private Set<String> readRoles;
    private Set<String> writeRoles;
    private boolean masked;
    private String maskPattern; // e.g., "XXX-XXX-****" for partial display
}
