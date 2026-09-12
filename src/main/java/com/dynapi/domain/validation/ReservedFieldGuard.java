package com.dynapi.domain.validation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class ReservedFieldGuard {
    public static final Set<String> RESERVED_FIELDS =
            Set.of("_id", "_class", "deleted", "deletedAt", "deletedBy");

    private ReservedFieldGuard() {
    }

    /**
     * Rejects reserved field names anywhere in the payload, including inside nested objects and
     * arrays of objects — not just at the top level — so a reserved key can't be smuggled in under
     * a nested path.
     */
    public static void reject(Map<String, Object> data) {
        if (data == null) {
            throw new IllegalArgumentException("Record data must not be null");
        }
        rejectRecursive(data);
    }

    private static void rejectRecursive(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object rawKey = entry.getKey();
                if (!(rawKey instanceof String key) || key.isBlank()) {
                    throw new IllegalArgumentException("Record field name must not be blank");
                }
                if (RESERVED_FIELDS.contains(key)) {
                    throw new IllegalArgumentException(
                            "Reserved field is not allowed in payload: " + key);
                }
                rejectRecursive(entry.getValue());
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                rejectRecursive(item);
            }
        }
    }
}
