package com.dynapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@ExtendWith(MockitoExtension.class)
class UniqueFieldConstraintServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    private UniqueFieldConstraintService uniqueFieldConstraintService;

    @BeforeEach
    void setUp() {
        uniqueFieldConstraintService = new UniqueFieldConstraintService(mongoTemplate);
    }

    @Test
    void validateForCreate_throwsWhenDuplicateExists() {
        FieldDefinition email = uniqueStringField("email");
        Map<String, Object> payload = Map.of("email", "alice@dynapi.dev");

        when(mongoTemplate.exists(any(Query.class), eq("users"))).thenReturn(true);

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> uniqueFieldConstraintService.validateForCreate("users", payload, List.of(email)));

        assertTrue(ex.getMessage().contains("Unique field violation for 'email'"));
    }

    @Test
    void validateForCreate_skipsCheckWhenUniqueValueMissing() {
        FieldDefinition email = uniqueStringField("email");
        Map<String, Object> payload = Map.of("name", "Alice");

        uniqueFieldConstraintService.validateForCreate("users", payload, List.of(email));

        verify(mongoTemplate, never()).exists(any(Query.class), eq("users"));
    }

    @Test
    void validateForUpdate_addsRecordIdExclusion() {
        FieldDefinition email = uniqueStringField("email");
        Map<String, Object> payload = Map.of("email", "alice@dynapi.dev");
        ObjectId objectId = new ObjectId();

        when(mongoTemplate.exists(any(Query.class), eq("users"))).thenReturn(false);

        uniqueFieldConstraintService.validateForUpdate("users", objectId, payload, List.of(email));

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).exists(queryCaptor.capture(), eq("users"));

        Document query = queryCaptor.getValue().getQueryObject();
        assertEquals("alice@dynapi.dev", query.get("email"));
        assertEquals(true, ((Document) query.get("deleted")).get("$ne"));
        assertEquals(objectId, ((Document) query.get("_id")).get("$ne"));
    }

    @Test
    void validateForCreate_rejectsUnsupportedUniqueType() {
        FieldDefinition objectField = new FieldDefinition();
        objectField.setFieldName("profile");
        objectField.setType(FieldType.OBJECT);
        objectField.setUnique(true);

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                uniqueFieldConstraintService.validateForCreate(
                                        "users", Map.of("profile", Map.of("city", "Istanbul")), List.of(objectField)));

        assertTrue(ex.getMessage().contains("Unique constraint is only supported"));
    }

    private FieldDefinition uniqueStringField(String name) {
        FieldDefinition field = new FieldDefinition();
        field.setFieldName(name);
        field.setType(FieldType.STRING);
        field.setUnique(true);
        return field;
    }
}
