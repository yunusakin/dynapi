package com.example.dynapi.domain.model;

import lombok.Data;
import java.util.Map;
import java.util.List;

@Data
public class DynamicQuery {
    private List<QueryFilter> filters;
    private List<String> sortFields;
    private List<String> sortDirections;
    private int page;
    private int size;
    
    @Data
    public static class QueryFilter {
        private String field;
        private String operator;  // eq, ne, gt, lt, in, etc.
        private Object value;
        private String combinator; // AND, OR
        private List<QueryFilter> subFilters;
    }
}
