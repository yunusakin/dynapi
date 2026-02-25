package com.dynapi.controller;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.FormRecordDto;
import com.dynapi.dto.RecordMutationRequest;
import com.dynapi.service.DynamicRecordService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/records", version = "1")
@RequiredArgsConstructor
public class RecordController {
  private final DynamicRecordService dynamicRecordService;

  @PatchMapping("/{entity}/{id}")
  @Operation(
      summary = "Patch Dynamic Record",
      description = "Partially updates a dynamic record and validates result against published schema.")
  public ApiResponse<FormRecordDto> patchRecord(
      @PathVariable String entity,
      @PathVariable String id,
      @RequestBody @Valid RecordMutationRequest request) {
    FormRecordDto updated =
        dynamicRecordService.patch(entity, id, request, LocaleContextHolder.getLocale());
    return ApiResponse.success(updated, "Record patched successfully");
  }

  @PutMapping("/{entity}/{id}")
  @Operation(
      summary = "Replace Dynamic Record",
      description = "Replaces a dynamic record and validates payload against published schema.")
  public ApiResponse<FormRecordDto> replaceRecord(
      @PathVariable String entity,
      @PathVariable String id,
      @RequestBody @Valid RecordMutationRequest request) {
    FormRecordDto updated =
        dynamicRecordService.replace(entity, id, request, LocaleContextHolder.getLocale());
    return ApiResponse.success(updated, "Record replaced successfully");
  }

  @DeleteMapping("/{entity}/{id}")
  @Operation(
      summary = "Soft Delete Dynamic Record",
      description = "Marks a dynamic record as deleted. Deleted records are excluded from queries.")
  public ApiResponse<Void> deleteRecord(@PathVariable String entity, @PathVariable String id) {
    dynamicRecordService.softDelete(entity, id);
    return ApiResponse.success(null, "Record deleted successfully");
  }
}
