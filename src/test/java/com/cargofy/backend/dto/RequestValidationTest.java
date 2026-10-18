package com.cargofy.backend.dto;

import com.cargofy.backend.model.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestValidationTest {

    private static final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private <T> Set<String> messages(T target) {
        return validator.validate(target).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    // ---- RegisterRequest ----

    private RegisterRequest validRegister() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alex Morgan");
        request.setEmail("alex@company.com");
        request.setPassword("password123");
        request.setRole(Role.CLIENT);
        return request;
    }

    @Test
    void registerRequest_validDataHasNoViolations() {
        assertTrue(messages(validRegister()).isEmpty());
    }

    @Test
    void registerRequest_companyNameIsOptional() {
        RegisterRequest request = validRegister();
        request.setCompanyName(null);

        assertTrue(messages(request).isEmpty());
    }

    @Test
    void registerRequest_requiresName() {
        RegisterRequest request = validRegister();
        request.setName(" ");

        assertTrue(messages(request).contains("Name is required"));
    }

    @Test
    void registerRequest_rejectsInvalidEmail() {
        RegisterRequest request = validRegister();
        request.setEmail("not-an-email");

        assertTrue(messages(request).contains("Email must be valid"));
    }

    @Test
    void registerRequest_rejectsShortPassword() {
        RegisterRequest request = validRegister();
        request.setPassword("short");

        assertTrue(messages(request).contains("Password must be at least 8 characters"));
    }

    @Test
    void registerRequest_requiresRole() {
        RegisterRequest request = validRegister();
        request.setRole(null);

        assertTrue(messages(request).contains("Role is required"));
    }

    // ---- LoginRequest ----

    @Test
    void loginRequest_validDataHasNoViolations() {
        LoginRequest request = new LoginRequest();
        request.setEmail("alex@company.com");
        request.setPassword("secret");

        assertTrue(messages(request).isEmpty());
    }

    @Test
    void loginRequest_requiresEmailAndPassword() {
        Set<String> messages = messages(new LoginRequest());

        assertTrue(messages.contains("Email is required"));
        assertTrue(messages.contains("Password is required"));
    }

    @Test
    void loginRequest_rejectsInvalidEmail() {
        LoginRequest request = new LoginRequest();
        request.setEmail("alex@");
        request.setPassword("secret");

        assertEquals(Set.of("Email must be valid"), messages(request));
    }

    // ---- UpdateUserRequest ----

    @Test
    void updateUserRequest_validDataHasNoViolations() {
        UpdateUserRequest request = new UpdateUserRequest("Alex Morgan", "alex@company.com", null);

        assertTrue(messages(request).isEmpty());
    }

    @Test
    void updateUserRequest_requiresNameAndEmail() {
        Set<String> messages = messages(new UpdateUserRequest(" ", "", null));

        assertTrue(messages.contains("Name is required"));
        assertTrue(messages.contains("Email is required"));
    }

    @Test
    void updateUserRequest_rejectsInvalidEmail() {
        UpdateUserRequest request = new UpdateUserRequest("Alex Morgan", "abc", "Acme");

        assertEquals(Set.of("Email must be valid"), messages(request));
    }

    // ---- ChangePasswordRequest ----

    @Test
    void changePasswordRequest_validDataHasNoViolations() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldpassword1", "newpassword1");

        assertTrue(messages(request).isEmpty());
    }

    @Test
    void changePasswordRequest_requiresBothPasswords() {
        Set<String> messages = messages(new ChangePasswordRequest("", ""));

        assertTrue(messages.contains("Current password is required"));
        assertTrue(messages.contains("New password is required"));
    }

    @Test
    void changePasswordRequest_rejectsShortNewPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldpassword1", "short");

        assertEquals(Set.of("New password must be at least 8 characters"), messages(request));
    }

    // ---- CreateShipmentRequest ----

    private CreateShipmentRequest validShipment() {
        CreateShipmentRequest request = new CreateShipmentRequest();
        request.setOrigin("Port of Hamburg");
        request.setDestination("Port of New York");
        return request;
    }

    @Test
    void createShipmentRequest_validDataHasNoViolations() {
        assertTrue(messages(validShipment()).isEmpty());
    }

    @Test
    void createShipmentRequest_requiresOriginAndDestination() {
        Set<String> messages = messages(new CreateShipmentRequest());

        assertTrue(messages.contains("Origin is required"));
        assertTrue(messages.contains("Destination is required"));
    }

    @Test
    void createShipmentRequest_acceptsANoteOfExactlyTheMaximumLength() {
        CreateShipmentRequest request = validShipment();
        request.setNote("a".repeat(300));

        assertTrue(messages(request).isEmpty());
    }

    @Test
    void createShipmentRequest_rejectsANoteThatIsTooLong() {
        CreateShipmentRequest request = validShipment();
        request.setNote("a".repeat(301));

        assertEquals(Set.of("Note must be at most 300 characters"), messages(request));
    }
}
