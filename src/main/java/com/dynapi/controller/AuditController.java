package com.dynapi.controller;

import com.dynapi.domain.model.AuditEntry;
import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.PaginatedResponse;
import com.dynapi.service.AuditService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/admin/audit", version = "1")
@RequiredArgsConstructor
public class AuditController {
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<PaginatedResponse<AuditEntry>> queryAuditLog(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        PaginatedResponse<AuditEntry> result =
                auditService.query(entityType, entityId, action, page, size);
        return ApiResponse.success(result, "Fetched");
    }
}
