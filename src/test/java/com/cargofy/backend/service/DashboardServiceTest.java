package com.cargofy.backend.service;

import com.cargofy.backend.dto.DashboardSummaryResponse;
import com.cargofy.backend.exception.ResourceNotFoundException;
import com.cargofy.backend.model.Role;
import com.cargofy.backend.model.Shipment;
import com.cargofy.backend.model.ShipmentStatus;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.ShipmentRepository;
import com.cargofy.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cargofy.backend.model.PackageType;
import com.cargofy.backend.model.TransportMode;

import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private User user(Long id, String email, Role role) {
        User user = new User();
        user.setId(id);
        user.setName("Test User");
        user.setEmail(email);
        user.setRole(role);
        return user;
    }

    private Shipment shipment(Long id, String trackingNumber, ShipmentStatus status) {
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setTrackingNumber(trackingNumber);
        shipment.setOrigin("Madrid");
        shipment.setDestination("Berlin");
        shipment.setStatus(status);
        shipment.setCreatedDate(LocalDateTime.now());
        return shipment;
    }

    @Test
    @DisplayName("a client gets the counts and recent shipments of their own shipments only")
    void getSummary_client_usesClientScopedCounts() {
        User client = user(7L, "client@cargofy.com", Role.CLIENT);
        when(userRepository.findByEmail("client@cargofy.com")).thenReturn(Optional.of(client));
        when(shipmentRepository.countByClientId(7L)).thenReturn(5L);
        when(shipmentRepository.countByClientIdAndStatus(7L, ShipmentStatus.PREPARING)).thenReturn(1L);
        when(shipmentRepository.countByClientIdAndStatus(7L, ShipmentStatus.CUSTOMS)).thenReturn(1L);
        when(shipmentRepository.countByClientIdAndStatus(7L, ShipmentStatus.IN_TRANSIT)).thenReturn(2L);
        when(shipmentRepository.countByClientIdAndStatus(7L, ShipmentStatus.DELIVERED)).thenReturn(1L);
        when(shipmentRepository.findTop5ByClientIdOrderByCreatedDateDesc(7L))
                .thenReturn(List.of(shipment(1L, "TRK-AAAA1111", ShipmentStatus.IN_TRANSIT)));

        DashboardSummaryResponse summary = dashboardService.getSummary("client@cargofy.com");

        assertEquals(5, summary.getTotal());
        assertEquals(1, summary.getPreparing());
        assertEquals(1, summary.getCustoms());
        assertEquals(2, summary.getInTransit());
        assertEquals(1, summary.getDelivered());
        assertEquals(1, summary.getRecentShipments().size());
        assertEquals("TRK-AAAA1111", summary.getRecentShipments().get(0).getTrackingNumber());

        verify(shipmentRepository, never()).count();
        verify(shipmentRepository, never()).findTop5ByOrderByCreatedDateDesc();
    }

    @Test
    @DisplayName("an operator gets the counts and recent shipments of all shipments")
    void getSummary_operator_usesGlobalCounts() {
        User operator = user(2L, "operator@cargofy.com", Role.OPERATOR);
        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));
        when(shipmentRepository.count()).thenReturn(10L);
        when(shipmentRepository.countByStatus(ShipmentStatus.PREPARING)).thenReturn(3L);
        when(shipmentRepository.countByStatus(ShipmentStatus.CUSTOMS)).thenReturn(2L);
        when(shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT)).thenReturn(4L);
        when(shipmentRepository.countByStatus(ShipmentStatus.DELIVERED)).thenReturn(1L);
        when(shipmentRepository.findTop5ByOrderByCreatedDateDesc()).thenReturn(List.of(
                shipment(1L, "TRK-AAAA1111", ShipmentStatus.PREPARING),
                shipment(2L, "TRK-BBBB2222", ShipmentStatus.DELIVERED)));

        DashboardSummaryResponse summary = dashboardService.getSummary("operator@cargofy.com");

        assertEquals(10, summary.getTotal());
        assertEquals(3, summary.getPreparing());
        assertEquals(2, summary.getCustoms());
        assertEquals(4, summary.getInTransit());
        assertEquals(1, summary.getDelivered());
        assertEquals(2, summary.getRecentShipments().size());

        verify(shipmentRepository, never()).countByClientId(any());
        verify(shipmentRepository, never()).findTop5ByClientIdOrderByCreatedDateDesc(any());
    }

    @Test
    @DisplayName("an admin also gets the global summary")
    void getSummary_admin_usesGlobalCounts() {
        User admin = user(1L, "admin@cargofy.com", Role.ADMIN);
        when(userRepository.findByEmail("admin@cargofy.com")).thenReturn(Optional.of(admin));
        when(shipmentRepository.count()).thenReturn(3L);

        DashboardSummaryResponse summary = dashboardService.getSummary("admin@cargofy.com");

        assertEquals(3, summary.getTotal());
        assertTrue(summary.getRecentShipments().isEmpty());
        verify(shipmentRepository, never()).countByClientId(any());
    }

    @Test
    @DisplayName("recent shipments carry the client and operator names, or null when missing")
    void getSummary_mapsClientAndOperatorNames() {
        User operator = user(2L, "operator@cargofy.com", Role.OPERATOR);
        User client = user(7L, "client@cargofy.com", Role.CLIENT);
        client.setName("Client A");

        Shipment withClient = shipment(1L, "TRK-AAAA1111", ShipmentStatus.PREPARING);
        withClient.setClient(client);
        Shipment withoutClient = shipment(2L, "TRK-BBBB2222", ShipmentStatus.PREPARING);

        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));
        when(shipmentRepository.findTop5ByOrderByCreatedDateDesc())
                .thenReturn(List.of(withClient, withoutClient));

        DashboardSummaryResponse summary = dashboardService.getSummary("operator@cargofy.com");

        assertEquals("Client A", summary.getRecentShipments().get(0).getClientName());
        assertEquals("PREPARING", summary.getRecentShipments().get(0).getStatus());
        assertNull(summary.getRecentShipments().get(1).getClientName());
        assertNull(summary.getRecentShipments().get(1).getAssignedOperatorName());
    }

    @Test
    @DisplayName("an unknown user gets a not found error and no data is read")
    void getSummary_unknownUser_throwsNotFound() {
        when(userRepository.findByEmail("ghost@cargofy.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dashboardService.getSummary("ghost@cargofy.com"));

        verifyNoInteractions(shipmentRepository);
    }

    
    @Test
    @DisplayName("recent shipments carry the cargo details and the estimated price")
    void getSummary_mapsCargoDetails() {
        User operator = user(2L, "operator@cargofy.com", Role.OPERATOR);
        Shipment cargo = shipment(1L, "TRK-AAAA1111", ShipmentStatus.PREPARING);
        cargo.setTransportMode(TransportMode.AIR);
        cargo.setPackageType(PackageType.BOX);
        cargo.setQuantity(4);
        cargo.setWeightKg(new BigDecimal("12.50"));
        cargo.setEstimatedPrice(new BigDecimal("93.25"));
        cargo.setDangerousGoods(true);
        cargo.setHsCode("847130");
        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));
        when(shipmentRepository.findTop5ByOrderByCreatedDateDesc()).thenReturn(List.of(cargo));

        DashboardSummaryResponse summary = dashboardService.getSummary("operator@cargofy.com");

        var recent = summary.getRecentShipments().get(0);
        assertEquals("AIR", recent.getTransportMode());
        assertEquals("BOX", recent.getPackageType());
        assertEquals(4, recent.getQuantity());
        assertEquals(new BigDecimal("93.25"), recent.getEstimatedPrice());
        assertTrue(recent.getDangerousGoods());
        assertEquals("847130", recent.getHsCode());
    }
}
