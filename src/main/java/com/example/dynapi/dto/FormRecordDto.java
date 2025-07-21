package com.example.dynapi.dto;

import lombok.Data;
import java.util.Map;

@Data
public class FormRecordDto {
    private String id;
    private Map<String, Object> data;
}
