package com.dynapi.domain.validation;

import com.dynapi.domain.exception.ValidationException;
import com.dynapi.domain.model.FieldDefinition;
import com.dynapi.domain.model.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicValidatorTest {

    private DynamicValidator dynamicValidator;

    @BeforeEach
    void setUp() {
        dynamicValidator = new DynamicValidator();
    }

    @Test
    void validate_acceptsPayload_whenRulesAreSatisfied() {
        FieldDefinition status = field("status", FieldType.STRING, true);
        status.setEnumValues(List.of("NEW", "DONE"));

        FieldDefinition title = field("title", FieldType.STRING, true);
        title.setMin(3.0);
        title.setMax(40.0);
        title.setRegex("^[A-Za-z ]+$");

        FieldDefinition score = field("score", FieldType.NUMBER, false);
        score.setMin(0.0);
        score.setMax(100.0);
        score.setRequiredIf(requiredIf("status", "DONE", "eq"));

        FieldDefinition profile = field("profile", FieldType.OBJECT, true);
        FieldDefinition age = field("age", FieldType.NUMBER, true);
        age.setMin(18.0);
        age.setMax(99.0);
        FieldDefinition email = field("email", FieldType.STRING, true);
        email.setRegex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        profile.setSubFields(List.of(age, email));

        FieldDefinition items = field("items", FieldType.ARRAY, true);
        items.setMin(1.0);
        items.setMax(5.0);
        FieldDefinition itemName = field("name", FieldType.STRING, true);
        itemName.setMin(2.0);
        FieldDefinition itemQty = field("qty", FieldType.NUMBER, true);
        itemQty.setMin(1.0);
        itemQty.setMax(10.0);
        items.setSubFields(List.of(itemName, itemQty));

        List<FieldDefinition> schema = List.of(status, title, score, profile, items);

        Map<String, Object> data = Map.of(
                "status", "DONE",
                "title", "Launch Build",
                "score", 92,
                "profile", Map.of(
                        "age", 24,
                        "email", "dev@dynapi.io"
                ),
                "items", List.of(
                        Map.of("name", "task", "qty", 2)
                )
        );

        assertDoesNotThrow(() -> dynamicValidator.validate(data, schema, Locale.US));
    }

    @Test
    void validate_rejectsMissingRequiredIfField() {
        FieldDefinition status = field("status", FieldType.STRING, true);
        FieldDefinition score = field("score", FieldType.NUMBER, false);
        score.setRequiredIf(requiredIf("status", "DONE", "eq"));
        score.setMin(0.0);

        Map<String, Object> data = Map.of("status", "DONE");

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> dynamicValidator.validate(data, List.of(status, score), Locale.US)
        );

        assertEquals("score", ex.getField());
        assertTrue(ex.getMessage().contains("required"));
    }

    @Test
    void validate_rejectsEnumViolation() {
        FieldDefinition status = field("status", FieldType.STRING, true);
        status.setEnumValues(List.of("NEW", "DONE"));

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> dynamicValidator.validate(Map.of("status", "ARCHIVED"), List.of(status), Locale.US)
        );

        assertEquals("status", ex.getField());
        assertTrue(ex.getMessage().contains("allowed enum"));
    }

    @Test
    void validate_rejectsRegexViolation_onNestedObject() {
        FieldDefinition profile = field("profile", FieldType.OBJECT, true);
        FieldDefinition email = field("email", FieldType.STRING, true);
        email.setRegex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        profile.setSubFields(List.of(email));

        Map<String, Object> data = Map.of(
                "profile", Map.of("email", "not-an-email")
        );

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> dynamicValidator.validate(data, List.of(profile), Locale.US)
        );

        assertEquals("profile.email", ex.getField());
        assertTrue(ex.getMessage().contains("pattern"));
    }

    @Test
    void validate_rejectsMinMaxViolation_onStringAndArray() {
        FieldDefinition title = field("title", FieldType.STRING, true);
        title.setMin(3.0);

        FieldDefinition tags = field("tags", FieldType.ARRAY, true);
        tags.setMax(2.0);

        Map<String, Object> shortTitleData = Map.of(
                "title", "Hi",
                "tags", List.of("a")
        );

        ValidationException titleEx = assertThrows(
                ValidationException.class,
                () -> dynamicValidator.validate(shortTitleData, List.of(title, tags), Locale.US)
        );
        assertEquals("title", titleEx.getField());

        Map<String, Object> oversizedArrayData = Map.of(
                "title", "Good",
                "tags", List.of("a", "b", "c")
        );

        ValidationException arrayEx = assertThrows(
                ValidationException.class,
                () -> dynamicValidator.validate(oversizedArrayData, List.of(title, tags), Locale.US)
        );
        assertEquals("tags", arrayEx.getField());
    }

    @Test
    void validate_rejectsNestedArrayItemViolation_withPrecisePath() {
        FieldDefinition items = field("items", FieldType.ARRAY, true);
        FieldDefinition qty = field("qty", FieldType.NUMBER, true);
        qty.setMin(1.0);
        items.setSubFields(List.of(qty));

        Map<String, Object> data = Map.of(
                "items", List.of(
                        Map.of("qty", 0)
                )
        );

        ValidationException ex = assertThrows(
                ValidationException.class,
                () -> dynamicValidator.validate(data, List.of(items), Locale.US)
        );

        assertEquals("items[0].qty", ex.getField());
    }

    private FieldDefinition field(String name, FieldType type, boolean required) {
        FieldDefinition definition = new FieldDefinition();
        definition.setFieldName(name);
        definition.setType(type);
        definition.setRequired(required);
        return definition;
    }

    private FieldDefinition.RequiredIfRule requiredIf(String field, Object value, String operator) {
        FieldDefinition.RequiredIfRule rule = new FieldDefinition.RequiredIfRule();
        rule.setField(field);
        rule.setValue(value);
        rule.setOperator(operator);
        return rule;
    }
}
