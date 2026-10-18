package com.cargofy.backend.dto;

import com.cargofy.backend.model.ShipmentStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateStatusRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("a request with a status and a note is valid")
    void validRequest_hasNoViolations() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(ShipmentStatus.IN_TRANSIT);
        request.setNote("Left the warehouse");

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("the note is optional")
    void missingNote_isValid() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(ShipmentStatus.DELIVERED);

        assertTrue(validator.validate(request).isEmpty());
    }

    @Test
    @DisplayName("a missing status is rejected with the expected message")
    void missingStatus_isRejected() {
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNote("No status given");

        Set<ConstraintViolation<UpdateStatusRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Status is required", violations.iterator().next().getMessage());
    }
}
