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
        String company = "OPERATOR".equals(role) ? "Cargofy" : "Test Co";
        String json = """
                {
                  "name": "%s",
                  "email": "%s",
                  "password": "Test12345",
                  "role": "%s",
                  "companyName": "%s",
                  "operatorAccessCode": "test-operator-code"
                }
                """.formatted(name, email, role, company);
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

    // The cargo fields every new shipment must carry (AIR, 2 pallets, 100 kg = 505.00 EUR).
    private static final String CARGO_FIELDS = """
            "transportMode": "AIR",
            "packageType": "PALLET",
            "quantity": 2,
            "weightKg": 100
            """;

    // Creates a shipment as the given operator and returns the raw JSON response.
    private String createShipment(String operatorToken, Long clientId, String origin) throws Exception {
        String json = """
                {
                  "origin": "%s",
                  "destination": "Berlin",
                  "clientId": %d,
                  %s
                }
                """.formatted(origin, clientId, CARGO_FIELDS);
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
    @DisplayName("operator creates a shipment: gets a tracking number, PREPARING status and the price")
    void operator_createsShipment_returnsTrackingNumberAndPreparing() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client One", "client1@cargofy.com", "CLIENT");
        long clientId = userId(client);

        String json = """
                {
                  "origin": "Madrid",
                  "destination": "Berlin",
                  "clientId": %d,
                  "transportMode": "AIR",
                  "packageType": "PALLET",
                  "quantity": 2,
                  "weightKg": 100,
                  "goodsDescription": "Machine parts",
                  "dangerousGoods": true,
                  "hsCode": "847130"
                }
                """.formatted(clientId);

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber", startsWith("TRK-")))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.origin").value("Madrid"))
                .andExpect(jsonPath("$.clientName").value("Client One"))
                .andExpect(jsonPath("$.transportMode").value("AIR"))
                .andExpect(jsonPath("$.packageType").value("PALLET"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.goodsDescription").value("Machine parts"))
                .andExpect(jsonPath("$.dangerousGoods").value(true))
                .andExpect(jsonPath("$.hsCode").value("847130"))
                .andExpect(jsonPath("$.estimatedPrice").value(505.0));
    }

    @Test
    @DisplayName("client cannot create a shipment (403)")
    void client_cannotCreateShipment_returnsForbidden() throws Exception {
        String client = register("Client One", "client1@cargofy.com", "CLIENT");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\", " + CARGO_FIELDS + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("creating a shipment without an origin returns 400")
    void createShipment_missingOrigin_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destination\": \"Berlin\", " + CARGO_FIELDS + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("creating a shipment without the cargo details returns 400 with field errors")
    void createShipment_missingCargoDetails_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.transportMode").value("Transport mode is required"))
                .andExpect(jsonPath("$.errors.packageType").value("Package type is required"))
                .andExpect(jsonPath("$.errors.quantity").value("Quantity is required"))
                .andExpect(jsonPath("$.errors.weightKg").value("Weight is required"));
    }

    @Test
    @DisplayName("creating a shipment with an invalid HS code returns 400")
    void createShipment_invalidHsCode_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\", \"hsCode\": \"12AB\", "
                                + CARGO_FIELDS + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.hsCode").value("HS code must contain 6 to 10 digits"));
    }

    @Test
    @DisplayName("creating a shipment with an unknown transport mode returns 400")
    void createShipment_unknownTransportMode_returnsBadRequest() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\", "
                                + "\"transportMode\": \"TRAIN\", \"packageType\": \"PALLET\", "
                                + "\"quantity\": 1, \"weightKg\": 10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("creating a shipment for an unknown client returns 404")
    void createShipment_unknownClient_returnsNotFound() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");

        mockMvc.perform(post("/api/shipments")
                        .header("Authorization", bearer(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origin\": \"Madrid\", \"destination\": \"Berlin\", \"clientId\": 999999, "
                                + CARGO_FIELDS + "}"))
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
    @DisplayName("client can open the detail of their own shipment, with the cargo details")
    void client_ownShipmentDetail_returnsOk() throws Exception {
        String operator = register("Operator One", "op1@cargofy.com", "OPERATOR");
        String client = register("Client A", "clienta@cargofy.com", "CLIENT");
        long id = shipmentId(createShipment(operator, userId(client), "Madrid"));

        mockMvc.perform(get("/api/shipments/" + id).header("Authorization", bearer(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin").value("Madrid"))
                .andExpect(jsonPath("$.transportMode").value("AIR"))
                .andExpect(jsonPath("$.packageType").value("PALLET"))
                .andExpect(jsonPath("$.estimatedPrice").value(505.0))
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