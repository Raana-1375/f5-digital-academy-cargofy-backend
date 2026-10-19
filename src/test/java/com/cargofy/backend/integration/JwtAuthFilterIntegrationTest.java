package com.cargofy.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAuthFilterIntegrationTest {

    private static final String LOGIN_BODY = """
            {"email": "nobody@cargofy.com", "password": "Whatever123"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWithMalformedTokenDoesNotFail500() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer not.a.valid.jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithMalformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/shipments")
                        .header("Authorization", "Bearer not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithGarbageTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized());
    }
}