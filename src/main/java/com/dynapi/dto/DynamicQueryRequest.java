
package com.dynapi.dto;
import java.util.List;

public class DynamicQueryRequest {
    private List<FilterRule> filters;
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;

    public List<FilterRule> getFilters() {
        return filters;
    }

    public void setFilters(List<FilterRule> filters) {
        this.filters = filters;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDirection() {
        return sortDirection;
    }

    public void setSortDirection(String sortDirection) {
        this.sortDirection = sortDirection;
    }
}
