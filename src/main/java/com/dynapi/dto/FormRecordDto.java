package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(name = "FormRecordDto", description = "Dynamic record returned by query endpoints.")
public record FormRecordDto(
    @Schema(description = "Record identifier.", example = "67bc267ab69ba95ca3407540") String id,
    @Schema(description = "Dynamic record payload.", example = "{\"name\":\"Alice\",\"age\":30}")
        Map<String, Object> data) {}
