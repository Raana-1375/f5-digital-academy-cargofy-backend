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

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ShipmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ---------- helpers ----------

    private String find(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        assertTrue(matcher.find(), "Pattern not found in: " + text);
        return matcher.group(1);
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

    private String bearer(String token) {
        return "Bearer " + token;
    }

    // Creates a shipment as the given operator and returns the raw JSON response.
    private String createShipment(String operatorToken, Long clientId, String origin) throws Exception {
        String json = """
                {
                  "origin": "%s",
                  "destination": "Berlin",
                  "clientId": %d
                }
                """.formatted(origin, clientId);
        return mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long shipmentId(String responseBody) {
        return Long.parseLong(find(responseBody, "\"id\"\\s*:\\s*(\\d+)"));
    }

    private void updateStatus(String token, long shipmentId, String status) throws Exception {
        mockMvc.perform(post("/api/shipments/" + shipmentId + "/status")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"" + status + "\", \"note\": \"Updated in test\"}"))
                .andExpect(status().isOk());
    }

    // ---------- creating shipments ----------

    @Test
    @DisplayName("operator creates a shipment: gets a tracking number and PREPARING status")
    void operator_createsShipment_returnsTrackingNumberAndPreparing() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client One", "client1@cargofy.com", "CLIENT");
        long clientId = userId(client);

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\", \"clientId\": " + clientId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber", startsWith("TRK-")))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.origin").value("Madrid"))
                .andExpect(jsonPath("$.clientName").value("Client One"));
    }

    @Test
    @DisplayName("client cannot create a shipment (403)")
    void client_cannotCreateShipment_returnsForbidden() throws Exception {
        String client = register("Client One", "client1@cargofy.com", "CLIENT");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("creating a shipment without an origin returns 400")
    void createShipment_missingOrigin_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\": \"Berlin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("creating a shipment for an unknown client returns 404")
    void createShipment_unknownClient_returnsNotFound() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\", \"clientId\": 999999}"))
                .andExpect(status().isNotFound());
    }

    // ---------- listing and access control ----------

    @Test
    @DisplayName("client only sees their own shipments")
    void client_listsOnlyOwnShipments() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String clientA = register("Client A", "clienta@cargofy.com", "CLIENT");
        String clientB = register("Client B", "clientb@cargofy.com", "CLIENT");
        createShipment(operator, userId(clientA), "Madrid");
        createShipment(operator, userId(clientB), "Paris");

        mockMvc.perform(get("/api/shipments").header("Authorization", bearer(clientA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("Madrid"));
    }

    @Test
    @DisplayName("operator sees the shipments of all clients")
    void operator_listsAllShipments() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String clientA = register("Client A", "clienta@cargofy.com", "CLIENT");
        String clientB = register("Client B", "clientb@cargofy.com", "CLIENT");
        createShipment(operator, userId(clientA), "Madrid");
        createShipment(operator, userId(clientB), "Paris");

        mockMvc.perform(get("/api/shipments").header("Authorization", bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("listing can be filtered by status")
    void listShipments_filterByStatus() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long clientId = userId(client);
        createShipment(operator, clientId, "Madrid");
        long second = shipmentId(createShipment(operator, clientId, "Paris"));
        updateStatus(operator, second, "CUSTOMS");

        mockMvc.perform(get("/api/shipments")
                        .param("status", "CUSTOMS")
                        .header("Authorization", bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("Paris"));
    }

    @Test
    @DisplayName("listing can be searched by tracking number")
    void listShipments_searchByTrackingNumber() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long clientId = userId(client);
        String created = createShipment(operator, clientId, "Madrid");
        createShipment(operator, clientId, "Paris");
        String trackingNumber = find(created, "\"trackingNumber\"\\s*:\\s*\"([^\"]+)\"");

        mockMvc.perform(get("/api/shipments")
                        .param("search", trackingNumber.toLowerCase())
                        .header("Authorization", bearer(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].trackingNumber").value(trackingNumber));
    }

    // ---------- shipment detail ----------

    @Test
    @DisplayName("client can open the detail of their own shipment")
    void client_ownShipmentDetail_returnsOk() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long id = shipmentId(createShipment(operator, userId(client), "Madrid"));

        mockMvc.perform(get("/api/shipments/" + id).header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("Madrid"))
                .andExpect(jsonPath("$.history").isEmpty());
    }

    @Test
    @DisplayName("client asking for another client's shipment gets 404")
    void client_otherClientsShipment_returnsNotFound() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String clientA = register("Client A", "clienta@cargofy.com", "CLIENT");
        String clientB = register("Client B", "clientb@cargofy.com", "CLIENT");
        long idOfB = shipmentId(createShipment(operator, userId(clientB), "Paris"));

        mockMvc.perform(get("/api/shipments/" + idOfB).header("Authorization", bearer(clientA)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Shipment not found"));
    }

    @Test
    @DisplayName("operator asking for a missing shipment gets 404")
    void operator_missingShipment_returnsNotFound() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(get("/api/shipments/999999").header("Authorization", bearer(operator)))
                .andExpect(status().isNotFound());
    }

    // ---------- status updates ----------

    @Test
    @DisplayName("operator updates the status and the change appears in the history")
    void operator_updatesStatus_andHistoryIsRecorded() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long id = shipmentId(createShipment(operator, userId(client), "Madrid"));

        mockMvc.perform(post("/api/shipments/" + id + "/status")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_TRANSIT\", \"note\": \"Left the warehouse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));

        mockMvc.perform(get("/api/shipments/" + id).header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.history[0].status").value("IN_TRANSIT"));
    }

    @Test
    @DisplayName("client cannot update a shipment status (403)")
    void client_cannotUpdateStatus_returnsForbidden() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long id = shipmentId(createShipment(operator, userId(client), "Madrid"));

        mockMvc.perform(post("/api/shipments/" + id + "/status")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"DELIVERED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("updating the status with an unknown value returns 400")
    void updateStatus_invalidValue_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long id = shipmentId(createShipment(operator, userId(client), "Madrid"));

        mockMvc.perform(post("/api/shipments/" + id + "/status")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"FLYING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("updating the status without a value returns 400")
    void updateStatus_missingValue_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long id = shipmentId(createShipment(operator, userId(client), "Madrid"));

        mockMvc.perform(post("/api/shipments/" + id + "/status")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"No status given\"}"))
                .andExpect(status().isBadRequest());
    }
}