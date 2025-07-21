package com.dynapi.domain.service;

import com.dynapi.domain.model.FieldDefinition;
import java.util.List;
import java.util.Map;

public interface DynamicValidator {
    void validate(Map<String, Object> data, List<FieldDefinition> schema);
}
