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

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------- helpers ----------

    private String find(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertTrue(matcher.find(), "Pattern not found in: " + text);
        return matcher.group(1);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String register(String name, String email, String role) throws Exception {
        String json = """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "Test12345",
                  "role": "%s",
                  "companyName": "Test Co"
                }
                """.formatted(name, email, role);
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return find(body, "\"token\"\\s*:\\s*\"([^\"]+)\"");
    }

    private long userId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(find(body, "\"id\"\\s*:\\s*(\\d+)"));
    }

    private String profileJson(String name, String email, String company) {
        return """
                {
                  "name": "%s",
                  "email": "%s",
                  "companyName": "%s"
                }
                """.formatted(name, email, company);
    }

    private String passwordJson(String current, String next) {
        return """
                {
                  "currentPassword": "%s",
                  "newPassword": "%s"
                }
                """.formatted(current, next);
    }

    private String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    // ---------- current user ----------

    @Test
    @DisplayName("GET /me returns the profile of the logged in user")
    void getCurrentUser_returnsProfile() throws Exception {
        String token = register("Test User", "me@cargofy.com", "CLIENT");

        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("me@cargofy.com"))
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andExpect(jsonPath("$.companyName").value("Test Co"));
    }

    @Test
    @DisplayName("GET /me without a token returns 401")
    void getCurrentUser_withoutToken_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- update profile ----------

    @Test
    @DisplayName("updating the profile returns the new data and a fresh token")
    void updateProfile_validData_returnsUserAndToken() throws Exception {
        String token = register("Test User", "me@cargofy.com", "CLIENT");
        long id = userId(token);

        mockMvc.perform(put("/api/users/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("New Name", "me@cargofy.com", "New Company")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("New Name"))
                .andExpect(jsonPath("$.user.companyName").value("New Company"))
                .andExpect(jsonPath("$.token", not(emptyOrNullString())));
    }

    @Test
    @DisplayName("after changing the email the user can log in with the new one")
    void updateProfile_changedEmail_canLoginWithNewEmail() throws Exception {
        String token = register("Test User", "old@cargofy.com", "CLIENT");
        long id = userId(token);

        mockMvc.perform(put("/api/users/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("Test User", "new@cargofy.com", "Test Co")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("new@cargofy.com"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("new@cargofy.com", "Test12345")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updating another user's profile returns 403")
    void updateProfile_otherUser_returnsForbidden() throws Exception {
        String tokenA = register("User A", "a@cargofy.com", "CLIENT");
        String tokenB = register("User B", "b@cargofy.com", "CLIENT");
        long idOfB = userId(tokenB);

        mockMvc.perform(put("/api/users/" + idOfB)
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("Hacked", "b@cargofy.com", "Test Co")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only update your own profile"));
    }

    @Test
    @DisplayName("changing the email to one already in use returns 409")
    void updateProfile_emailInUse_returnsConflict() throws Exception {
        String tokenA = register("User A", "a@cargofy.com", "CLIENT");
        register("User B", "b@cargofy.com", "CLIENT");
        long idOfA = userId(tokenA);

        mockMvc.perform(put("/api/users/" + idOfA)
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("User A", "b@cargofy.com", "Test Co")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already in use"));
    }

    @Test
    @DisplayName("updating the profile with a blank name returns 400 with field errors")
    void updateProfile_blankName_returnsBadRequest() throws Exception {
        String token = register("Test User", "me@cargofy.com", "CLIENT");
        long id = userId(token);

        mockMvc.perform(put("/api/users/" + id)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profileJson("", "me@cargofy.com", "Test Co")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Name is required"));
    }

    // ---------- change password ----------

    @Test
    @DisplayName("changing the password works: the new one logs in, the old one does not")
    void changePassword_valid_newPasswordWorks() throws Exception {
        String token = register("Test User", "me@cargofy.com", "CLIENT");
        long id = userId(token);

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("Test12345", "BrandNew123")))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("me@cargofy.com", "BrandNew123")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("me@cargofy.com", "Test12345")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("changing the password with a wrong current password returns 400")
    void changePassword_wrongCurrentPassword_returnsBadRequest() throws Exception {
        String token = register("Test User", "me@cargofy.com", "CLIENT");
        long id = userId(token);

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("WrongPassword1", "BrandNew123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    @DisplayName("using the same password as the new one returns 400")
    void changePassword_samePassword_returnsBadRequest() throws Exception {
        String token = register("Test User", "me@cargofy.com", "CLIENT");
        long id = userId(token);

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("Test12345", "Test12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("New password must be different from the current password"));
    }

    @Test
    @DisplayName("a too short new password returns 400")
    void changePassword_shortNewPassword_returnsBadRequest() throws Exception {
        String token = register("Test User", "me@cargofy.com", "CLIENT");
        long id = userId(token);

        mockMvc.perform(put("/api/users/" + id + "/password")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("Test12345", "123")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("changing another user's password returns 403")
    void changePassword_otherUser_returnsForbidden() throws Exception {
        String tokenA = register("User A", "a@cargofy.com", "CLIENT");
        String tokenB = register("User B", "b@cargofy.com", "CLIENT");
        long idOfB = userId(tokenB);

        mockMvc.perform(put("/api/users/" + idOfB + "/password")
                        .header("Authorization", bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(passwordJson("Test12345", "BrandNew123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only change your own password"));
    }

    // ---------- listing users by role ----------

    @Test
    @DisplayName("operator can list the clients")
    void listUsersByRole_asOperator_returnsClients() throws Exception {
        String operator = register("Operator One", "op@cargofy.com", "OPERATOR");
        register("Client A", "a@cargofy.com", "CLIENT");
        register("Client B", "b@cargofy.com", "CLIENT");

        mockMvc.perform(get("/api/users")
                        .param("role", "CLIENT")
                        .header("Authorization", bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    @DisplayName("client cannot list users (403)")
    void listUsersByRole_asClient_returnsForbidden() throws Exception {
        String client = register("Client A", "a@cargofy.com", "CLIENT");

        mockMvc.perform(get("/api/users")
                        .param("role", "CLIENT")
                        .header("Authorization", bearer(client)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("listing users without the role parameter returns 400")
    void listUsersByRole_missingRole_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op@cargofy.com", "OPERATOR");

        mockMvc.perform(get("/api/users").header("Authorization", bearer(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required parameter 'role'"));
    }
}
