package com.dynapi.domain.model;

import java.util.List;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document
public class FieldGroup {
    @Id
    private String id;
    private String name;
    private String entity;
    private List<String> fieldNames;
    private Integer version; // Schema versioning
    private List<String> permissions; // Field-level permissions
}
