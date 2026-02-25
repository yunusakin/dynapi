package com.dynapi.domain.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class FieldDefinition {
    @Id
    private String id;
    @NotBlank
    private String fieldName;
    @NotNull
    private FieldType type;
    private boolean required;
    private boolean unique;
    private boolean indexed;
    private Double min;
    private Double max;
    private String regex;
    private List<Object> enumValues;
    private RequiredIfRule requiredIf;
    private List<FieldDefinition> subFields;
    private Integer version; // Schema versioning
    private List<String> permissions; // Field-level permissions

    @Data
    public static class RequiredIfRule {
        @NotBlank
        private String field;
        private Object value;
        private String operator = "eq";
    }
}
