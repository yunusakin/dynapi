package com.example.dynapi.dto;

import lombok.Data;
import java.util.List;

@Data
public class DynamicQueryRequest {
    private List<FilterRule> filters;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}
