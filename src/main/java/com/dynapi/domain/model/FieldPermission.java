package com.dynapi.domain.model;

import java.util.Set;

import lombok.Data;

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
