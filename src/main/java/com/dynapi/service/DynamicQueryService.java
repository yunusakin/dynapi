package com.dynapi.service;

import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DynamicQueryService {
    @Autowired
    private MongoTemplate mongoTemplate;

    public PaginatedResponse<FormRecordDto> query(String entity, DynamicQueryRequest request) {
        Query query = new Query();
        // Build advanced filters
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            query.addCriteria(buildCriteria(request.getFilters()));
        }
        // Pagination and sorting
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 10;
        if (request.getSortBy() != null && request.getSortDirection() != null) {
            Sort.Direction dir = request.getSortDirection().equalsIgnoreCase("DESC") ? Sort.Direction.DESC : Sort.Direction.ASC;
            query.with(Sort.by(dir, request.getSortBy()));
        }
        query.with(PageRequest.of(page, size));
        // Query MongoDB
        List<Map> results = mongoTemplate.find(query, Map.class, entity);
        // Map to FormRecordDto (strip _id, _class)
        List<FormRecordDto> content = results.stream().map(map -> {
            FormRecordDto dto = new FormRecordDto();
            dto.setId(map.get("_id") != null ? map.get("_id").toString() : null);
            map.remove("_id");
            map.remove("_class");
            dto.setData(map);
            return dto;
        }).collect(Collectors.toList());
        // Total count
        long total = mongoTemplate.count(query.skip(-1).limit(-1), entity);
        // Paginated response
        PaginatedResponse<FormRecordDto> response = new PaginatedResponse<>();
        response.setPage(page);
        response.setSize(size);
        response.setTotalElements(total);
        response.setContent(content);
        response.setSortBy(request.getSortBy());
        response.setSortDirection(request.getSortDirection());
        return response;
    }

    // Recursively build Criteria from FilterRule list
    private Criteria buildCriteria(List<com.dynapi.dto.FilterRule> rules) {
        if (rules == null || rules.isEmpty()) return new Criteria();
        List<Criteria> criteriaList = rules.stream().map(this::buildCriteria).collect(Collectors.toList());
        // Default to AND
        return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
    }

    private Criteria buildCriteria(com.dynapi.dto.FilterRule rule) {
        if (rule.getOperator() == null || rule.getOperator().equalsIgnoreCase("eq")) {
            return Criteria.where(rule.getField()).is(rule.getValue());
        }
        switch (rule.getOperator().toLowerCase()) {
            case "ne":
                return Criteria.where(rule.getField()).ne(rule.getValue());
            case "gt":
                return Criteria.where(rule.getField()).gt(rule.getValue());
            case "lt":
                return Criteria.where(rule.getField()).lt(rule.getValue());
            case "gte":
                return Criteria.where(rule.getField()).gte(rule.getValue());
            case "lte":
                return Criteria.where(rule.getField()).lte(rule.getValue());
            case "in":
                return Criteria.where(rule.getField()).in((List<?>) rule.getValue());
            case "regex":
                return Criteria.where(rule.getField()).regex(rule.getValue().toString());
            case "and":
                return new Criteria().andOperator(rule.getRules().stream().map(this::buildCriteria).toArray(Criteria[]::new));
            case "or":
                return new Criteria().orOperator(rule.getRules().stream().map(this::buildCriteria).toArray(Criteria[]::new));
            case "not":
                return new Criteria().not().elemMatch(buildCriteria(rule.getRules().get(0)));
            default:
                return Criteria.where(rule.getField()).is(rule.getValue());
        }
    }
}
