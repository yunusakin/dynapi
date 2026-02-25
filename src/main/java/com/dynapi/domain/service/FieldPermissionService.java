package com.dynapi.domain.service;

import com.dynapi.domain.model.FieldPermission;

import java.util.Map;
import java.util.Set;

public interface FieldPermissionService {
    boolean hasReadPermission(String fieldName, String entityName, Set<String> userRoles);

    boolean hasWritePermission(String fieldName, String entityName, Set<String> userRoles);

    Map<String, Object> applyFieldMasking(
            Map<String, Object> data, String entityName, Set<String> userRoles);

    FieldPermission setFieldPermissions(
            String fieldName, String entityName, Set<String> readRoles, Set<String> writeRoles);

    void removeFieldPermissions(String fieldName, String entityName);
}
