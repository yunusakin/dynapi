package com.dynapi.controller;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.DynamicQueryRequest;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.service.DynamicQueryService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/query", version = "1")
@RequiredArgsConstructor
public class QueryController {
    private final DynamicQueryService dynamicQueryService;

    @PostMapping("/{entity}")
    @Operation(
            summary = "Query Dynamic Records",
            description = "Queries records for an entity using filters, pagination, and sorting.")
    public ApiResponse<PaginatedResponse<FormRecordDto>> query(
            @PathVariable String entity, @RequestBody @Valid DynamicQueryRequest request) {
        PaginatedResponse<FormRecordDto> result = dynamicQueryService.query(entity, request);
        return ApiResponse.success(result, "Query successful");
    }
}
