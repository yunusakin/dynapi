package com.dynapi.application.query;

import com.dynapi.domain.model.DynamicQuery;
import org.springframework.data.domain.Page;
import java.util.Map;

public interface DynamicQueryService {
    Page<Map<String, Object>> executeQuery(String entityName, DynamicQuery query);
}
