package com.dynapi.controller;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.service.DynamicQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
public class QueryController {
    @Autowired
    private DynamicQueryService dynamicQueryService;

    @PostMapping("/{entity}")
    public ApiResponse<PaginatedResponse<FormRecordDto>> query(
            @PathVariable String entity,
            @RequestBody DynamicQueryRequest request) {
        PaginatedResponse<FormRecordDto> result = dynamicQueryService.query(entity, request);
        return new ApiResponse<>(true, "Query successful", result);
    }
}
