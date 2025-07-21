package com.example.dynapi.infrastructure.persistence;

import com.example.dynapi.application.query.DynamicQueryService;
import com.example.dynapi.domain.model.DynamicQuery;
import com.example.dynapi.domain.model.DynamicQuery.QueryFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MongoDynamicQueryService implements DynamicQueryService {
    private final MongoTemplate mongoTemplate;

    public MongoDynamicQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Page<Map<String, Object>> executeQuery(String entityName, DynamicQuery dynamicQuery) {
        Query query = new Query();
        
        // Apply filters
        if (dynamicQuery.getFilters() != null && !dynamicQuery.getFilters().isEmpty()) {
            query.addCriteria(buildCriteria(dynamicQuery.getFilters()));
        }

        // Apply sorting
        if (dynamicQuery.getSortFields() != null && !dynamicQuery.getSortFields().isEmpty()) {
            for (int i = 0; i < dynamicQuery.getSortFields().size(); i++) {
                String direction = i < dynamicQuery.getSortDirections().size() ? 
                    dynamicQuery.getSortDirections().get(i) : "ASC";
                query.with(org.springframework.data.domain.Sort.by(
                    direction.equalsIgnoreCase("DESC") ? 
                    org.springframework.data.domain.Sort.Direction.DESC : 
                    org.springframework.data.domain.Sort.Direction.ASC,
                    dynamicQuery.getSortFields().get(i)));
            }
        }

        // Get total count
        long total = mongoTemplate.count(query, entityName);

        // Apply pagination
        query.with(PageRequest.of(dynamicQuery.getPage(), dynamicQuery.getSize()));

        // Execute query
        List<Map<String, Object>> results = mongoTemplate.find(query, Map.class, entityName);

        // Remove MongoDB metadata
        results.forEach(map -> {
            map.remove("_id");
            map.remove("_class");
        });

        return new PageImpl<>(results, PageRequest.of(dynamicQuery.getPage(), dynamicQuery.getSize()), total);
    }

    private Criteria buildCriteria(List<QueryFilter> filters) {
        if (filters == null || filters.isEmpty()) {
            return new Criteria();
        }

        List<Criteria> criteriaList = new ArrayList<>();
        for (QueryFilter filter : filters) {
            if (filter.getSubFilters() != null && !filter.getSubFilters().isEmpty()) {
                criteriaList.add(buildCriteria(filter.getSubFilters()));
            } else {
                criteriaList.add(createSingleCriteria(filter));
            }
        }

        String combinator = filters.get(0).getCombinator();
        if ("OR".equalsIgnoreCase(combinator)) {
            return new Criteria().orOperator(criteriaList.toArray(new Criteria[0]));
        } else {
            return new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));
        }
    }

    private Criteria createSingleCriteria(QueryFilter filter) {
        Criteria criteria = Criteria.where(filter.getField());
        
        switch (filter.getOperator().toLowerCase()) {
            case "eq":
                return criteria.is(filter.getValue());
            case "ne":
                return criteria.ne(filter.getValue());
            case "gt":
                return criteria.gt(filter.getValue());
            case "lt":
                return criteria.lt(filter.getValue());
            case "gte":
                return criteria.gte(filter.getValue());
            case "lte":
                return criteria.lte(filter.getValue());
            case "in":
                return criteria.in(filter.getValue());
            case "nin":
                return criteria.nin(filter.getValue());
            case "regex":
                return criteria.regex(String.valueOf(filter.getValue()));
            default:
                throw new IllegalArgumentException("Unsupported operator: " + filter.getOperator());
        }
    }
}
