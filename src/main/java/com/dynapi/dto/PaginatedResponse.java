package com.dynapi.dto;

import lombok.Data;
import java.util.List;

@Data
public class PaginatedResponse<T> {
    private int page;
    private int size;
    private long totalElements;
    private List<T> content;
    private String sortBy;
    private String sortDirection;
}
