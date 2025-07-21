package com.dynapi.dto;

import lombok.Data;
import java.util.Map;

@Data
public class FormSubmissionRequest {
    private String group;
    private Map<String, Object> data;
}
