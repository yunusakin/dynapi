package com.dynapi.domain.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ReservedFieldGuardTest {

    @Test
    void reject_allowsPlainPayloadWithNoReservedFields() {
        assertDoesNotThrow(() -> ReservedFieldGuard.reject(Map.of("title", "Ship v1")));
    }

    @Test
    void reject_rejectsTopLevelReservedField() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ReservedFieldGuard.reject(Map.of("_id", "x", "title", "Ship v1")));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("_id"));
    }

    @Test
    void reject_rejectsReservedFieldNestedInsideAnObject() {
        Map<String, Object> payload = Map.of("profile", Map.of("_id", "sneaky"));

        assertThrows(IllegalArgumentException.class, () -> ReservedFieldGuard.reject(payload));
    }

    @Test
    void reject_rejectsReservedFieldNestedInsideAnArrayOfObjects() {
        Map<String, Object> payload = Map.of("items", List.of(Map.of("title", "ok"), Map.of("deletedBy", "x")));

        assertThrows(IllegalArgumentException.class, () -> ReservedFieldGuard.reject(payload));
    }

    @Test
    void reject_allowsNestedNonReservedFields() {
        Map<String, Object> payload = Map.of("profile", Map.of("age", 30, "city", "Istanbul"));

        assertDoesNotThrow(() -> ReservedFieldGuard.reject(payload));
    }

    @Test
    void reject_throwsOnNullData() {
        assertThrows(IllegalArgumentException.class, () -> ReservedFieldGuard.reject(null));
    }
}
