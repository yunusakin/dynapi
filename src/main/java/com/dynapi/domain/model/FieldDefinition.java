package com.dynapi.domain.model;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@Document
public class FieldDefinition {
    @NotBlank
    private String fieldName;
    @NotNull
    private FieldType type;
    private boolean required;
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
