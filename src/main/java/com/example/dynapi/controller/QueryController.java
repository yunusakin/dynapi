package com.example.dynapi.controller;

import com.example.dynapi.dto.ApiResponse;
import com.example.dynapi.dto.DynamicQueryRequest;
import com.example.dynapi.dto.FormRecordDto;
import com.example.dynapi.dto.PaginatedResponse;
import com.example.dynapi.service.DynamicQueryService;
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
