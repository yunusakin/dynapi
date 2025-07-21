package com.dynapi.controller;

import com.dynapi.dto.ApiResponse;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldGroup;
import com.dynapi.repository.FieldDefinitionRepository;
import com.dynapi.repository.FieldGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/schema")
@RequiredArgsConstructor
public class SchemaAdminController {
    private final FieldDefinitionRepository fieldDefinitionRepository;
    private final FieldGroupRepository fieldGroupRepository;

    // FieldDefinition CRUD
    @PostMapping("/field-definitions")
    public ApiResponse<FieldDefinition> createFieldDefinition(@RequestBody FieldDefinition def) {
        return new ApiResponse<>(true, "Created", fieldDefinitionRepository.save(def));
    }

    @PutMapping("/field-definitions/{id}")
    public ApiResponse<FieldDefinition> updateFieldDefinition(@PathVariable String id, @RequestBody FieldDefinition def) {
        def.setFieldName(id);
        return new ApiResponse<>(true, "Updated", fieldDefinitionRepository.save(def));
    }

    @DeleteMapping("/field-definitions/{id}")
    public ApiResponse<Void> deleteFieldDefinition(@PathVariable String id) {
        fieldDefinitionRepository.deleteById(id);
        return new ApiResponse<>(true, "Deleted", null);
    }

    @GetMapping("/field-definitions")
    public ApiResponse<List<FieldDefinition>> getAllFieldDefinitions() {
        return new ApiResponse<>(true, "Fetched", fieldDefinitionRepository.findAll());
    }

    // FieldGroup CRUD
    @PostMapping("/field-groups")
    public ApiResponse<FieldGroup> createFieldGroup(@RequestBody FieldGroup group) {
        return new ApiResponse<>(true, "Created", fieldGroupRepository.save(group));
    }

    @PutMapping("/field-groups/{id}")
    public ApiResponse<FieldGroup> updateFieldGroup(@PathVariable String id, @RequestBody FieldGroup group) {
        group.setName(id);
        return new ApiResponse<>(true, "Updated", fieldGroupRepository.save(group));
    }

    @DeleteMapping("/field-groups/{id}")
    public ApiResponse<Void> deleteFieldGroup(@PathVariable String id) {
        fieldGroupRepository.deleteById(id);
        return new ApiResponse<>(true, "Deleted", null);
    }

    @GetMapping("/field-groups")
    public ApiResponse<List<FieldGroup>> getAllFieldGroups() {
        return new ApiResponse<>(true, "Fetched", fieldGroupRepository.findAll());
    }
}
