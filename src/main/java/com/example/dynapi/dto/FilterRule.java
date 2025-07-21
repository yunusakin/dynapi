package com.example.dynapi.dto;

import lombok.Data;

@Data
public class FilterRule {
    private String field;
    private String operator; // e.g. eq, ne, gt, lt, in, regex, and, or, not
    private Object value;
    private java.util.List<FilterRule> rules; // for AND/OR/NOT combinators
}
