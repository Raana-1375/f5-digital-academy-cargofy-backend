package com.cargofy.backend.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    // Matches cargofy.operator-access-code in application-test.properties
    private static final String OPERATOR_CODE = "test-operator-code";

    @Autowired
    private MockMvc mockMvc;

    // A null code leaves the field out of the request body.
    private String registerJson(String email, String password, String role, String company, String code) {
        String codeField = code == null ? "" : ",\n  \"operatorAccessCode\": \"" + code + "\"";
        return """
                {
                  "name": "Test User",
                  "email": "%s",
                  "password": "%s",
                  "role": "%s",
                  "companyName": "%s"%s
                }
                """.formatted(email, password, role, company, codeField);
    }

    private String registerJson(String email, String password, String role) {
        String company = "OPERATOR".equals(role) ? "Cargofy" : "Test Co";
        return registerJson(email, password, role, company, OPERATOR_CODE);
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private String registerAndGetToken(String email, String role) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "Test12345", role)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        matcher.find();
        return matcher.group(1);
    }

    @Test
    @DisplayName("register with valid data returns a token and the user info")
    void register_validData_returnsTokenAndUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("new@cargofy.com", "Test12345", "CLIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.email").value("new@cargofy.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"));
    }

    @Test
    @DisplayName("register with an existing email returns 409 conflict")
    void register_duplicateEmail_returnsConflict() throws Exception {
        registerAndGetToken("dup@cargofy.com", "CLIENT");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("dup@cargofy.com", "Test12345", "CLIENT")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("register with a too short password returns 400")
    void register_shortPassword_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("short@cargofy.com", "123", "CLIENT")))
                .andExpect(status().isBadRequest());
    }

    // ---------- operator registration ----------

    @Test
    @DisplayName("operator with the right access code and company is created")
    void register_operatorWithValidCode_returnsOk() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("op@cargofy.com", "Test12345", "OPERATOR", "cargofy", OPERATOR_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    @DisplayName("operator with a wrong access code returns 403")
    void register_operatorWithWrongCode_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("op@cargofy.com", "Test12345", "OPERATOR", "Cargofy", "wrong-code")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid operator access code"));
    }

    @Test
    @DisplayName("operator without an access code returns 403")
    void register_operatorWithoutCode_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("op@cargofy.com", "Test12345", "OPERATOR", "Cargofy", null)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("operator with another company returns 400")
    void register_operatorWithOtherCompany_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("op@cargofy.com", "Test12345", "OPERATOR", "Other Co", OPERATOR_CODE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Operators must belong to Cargofy"));
    }

    // ---------- login and protected endpoints ----------

    @Test
    @DisplayName("login with correct credentials returns a token")
    void login_validCredentials_returnsToken() throws Exception {
        registerAndGetToken("login@cargofy.com", "CLIENT");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("login@cargofy.com", "Test12345")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyOrNullString())))
                .andExpect(jsonPath("$.email").value("login@cargofy.com"));
    }

    @Test
    @DisplayName("login with a wrong password is rejected")
    void login_wrongPassword_isRejected() throws Exception {
        registerAndGetToken("wrong@cargofy.com", "CLIENT");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("wrong@cargofy.com", "WrongPassword1")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("protected endpoint without a token returns 401")
    void protectedEndpoint_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/shipments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("protected endpoint with a valid token returns 200")
    void protectedEndpoint_withToken_returnsOk() throws Exception {
        String token = registerAndGetToken("token@cargofy.com", "CLIENT");

        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}