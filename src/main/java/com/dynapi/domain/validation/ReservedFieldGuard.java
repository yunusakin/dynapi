package com.dynapi.domain.validation;

import java.util.Map;
import java.util.Set;

public final class ReservedFieldGuard {
    public static final Set<String> RESERVED_FIELDS =
            Set.of("_id", "_class", "deleted", "deletedAt", "deletedBy");

    private ReservedFieldGuard() {
    }

    public static void reject(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("Record data must not be null");
        }
        for (String key : data.keySet()) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Record field name must not be blank");
            }
            if (RESERVED_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Reserved field is not allowed in payload: " + key);
            }
        }
    }
}
