package com.cargofy.backend.service;

import com.cargofy.backend.dto.UpdateStatusRequest;
import com.cargofy.backend.exception.ResourceNotFoundException;
import com.cargofy.backend.model.Role;
import com.cargofy.backend.model.Shipment;
import com.cargofy.backend.model.ShipmentStatus;
import com.cargofy.backend.model.StatusHistory;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.ShipmentRepository;
import com.cargofy.backend.repository.StatusHistoryRepository;
import com.cargofy.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StatusHistoryRepository statusHistoryRepository;

    @InjectMocks
    private ShipmentService shipmentService;

    private User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setRole(role);
        return user;
    }

    private Shipment shipment(Long id, User client) {
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setTrackingNumber("TRK-" + id);
        shipment.setOrigin("Hamburg");
        shipment.setDestination("New York");
        shipment.setStatus(ShipmentStatus.PREPARING);
        shipment.setCreatedDate(LocalDateTime.now());
        shipment.setClient(client);
        return shipment;
    }

    // ---- listShipments ----

    @Test
    void listShipments_clientOnlyGetsOwnShipments() {
        User client = user(7L, "Client", Role.CLIENT);
        when(userRepository.findByEmail("client@cargofy.com")).thenReturn(Optional.of(client));
        when(shipmentRepository.findByClientId(eq(7L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shipment(1L, client))));

        var result = shipmentService.listShipments(null, null, 0, 10, "client@cargofy.com");

        assertEquals(1, result.size());
        verify(shipmentRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listShipments_clientWithStatusFilterIsScopedToTheClient() {
        User client = user(7L, "Client", Role.CLIENT);
        when(userRepository.findByEmail("client@cargofy.com")).thenReturn(Optional.of(client));
        when(shipmentRepository.findByClientIdAndStatus(
                eq(7L), eq(ShipmentStatus.CUSTOMS), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = shipmentService.listShipments(
                ShipmentStatus.CUSTOMS, null, 0, 10, "client@cargofy.com");

        assertEquals(0, result.size());
        verify(shipmentRepository, never()).findByStatus(any(), any(Pageable.class));
    }

    @Test
    void listShipments_clientWithStatusAndSearchIsScopedToTheClient() {
        User client = user(7L, "Client", Role.CLIENT);
        when(userRepository.findByEmail("client@cargofy.com")).thenReturn(Optional.of(client));
        when(shipmentRepository.findByClientIdAndStatusAndTrackingNumberContainingIgnoreCase(
                eq(7L), eq(ShipmentStatus.PREPARING), eq("trk"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shipment(1L, client))));

        var result = shipmentService.listShipments(
                ShipmentStatus.PREPARING, " trk ", 0, 10, "client@cargofy.com");

        assertEquals(1, result.size());
    }

    @Test
    void listShipments_operatorGetsAllShipments() {
        User operator = user(2L, "Operator", Role.OPERATOR);
        User client = user(7L, "Client", Role.CLIENT);
        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));
        when(shipmentRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(shipment(1L, client), shipment(2L, null))));

        var result = shipmentService.listShipments(null, null, 0, 10, "operator@cargofy.com");

        assertEquals(2, result.size());
        verify(shipmentRepository, never()).findByClientId(any(), any(Pageable.class));
    }

    @Test
    void listShipments_operatorWithStatusFilterUsesTheGlobalQuery() {
        User operator = user(2L, "Operator", Role.OPERATOR);
        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));
        when(shipmentRepository.findByStatus(eq(ShipmentStatus.DELIVERED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        var result = shipmentService.listShipments(
                ShipmentStatus.DELIVERED, null, 0, 10, "operator@cargofy.com");

        assertEquals(0, result.size());
    }

    @Test
    void listShipments_unknownUserThrows() {
        when(userRepository.findByEmail("ghost@cargofy.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> shipmentService.listShipments(null, null, 0, 10, "ghost@cargofy.com"));
    }

    // ---- getShipmentDetail ----

    @Test
    void getShipmentDetail_clientCanSeeOwnShipment() {
        User client = user(7L, "Client", Role.CLIENT);
        when(userRepository.findByEmail("client@cargofy.com")).thenReturn(Optional.of(client));
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment(1L, client)));
        when(statusHistoryRepository.findByShipmentIdOrderByUpdatedDateAsc(1L)).thenReturn(List.of());

        assertNotNull(shipmentService.getShipmentDetail(1L, "client@cargofy.com"));
    }

    @Test
    void getShipmentDetail_clientCannotSeeAnotherClientsShipment() {
        User client = user(7L, "Client", Role.CLIENT);
        User other = user(8L, "Other", Role.CLIENT);
        when(userRepository.findByEmail("client@cargofy.com")).thenReturn(Optional.of(client));
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment(1L, other)));

        assertThrows(ResourceNotFoundException.class,
                () -> shipmentService.getShipmentDetail(1L, "client@cargofy.com"));
    }

    @Test
    void getShipmentDetail_clientCannotSeeAShipmentWithoutClient() {
        User client = user(7L, "Client", Role.CLIENT);
        when(userRepository.findByEmail("client@cargofy.com")).thenReturn(Optional.of(client));
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment(1L, null)));

        assertThrows(ResourceNotFoundException.class,
                () -> shipmentService.getShipmentDetail(1L, "client@cargofy.com"));
    }

    @Test
    void getShipmentDetail_operatorCanSeeAnyShipment() {
        User operator = user(2L, "Operator", Role.OPERATOR);
        User client = user(7L, "Client", Role.CLIENT);
        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment(1L, client)));
        when(statusHistoryRepository.findByShipmentIdOrderByUpdatedDateAsc(1L)).thenReturn(List.of());

        assertNotNull(shipmentService.getShipmentDetail(1L, "operator@cargofy.com"));
    }

    @Test
    void getShipmentDetail_unknownShipmentThrows() {
        User operator = user(2L, "Operator", Role.OPERATOR);
        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));
        when(shipmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> shipmentService.getShipmentDetail(99L, "operator@cargofy.com"));
    }

    // ---- updateStatus ----

    @Test
    void updateStatus_changesTheStatusAndRecordsHistory() {
        User operator = user(2L, "Operator", Role.OPERATOR);
        Shipment shipment = shipment(1L, null);
        when(shipmentRepository.findById(1L)).thenReturn(Optional.of(shipment));
        when(userRepository.findByEmail("operator@cargofy.com")).thenReturn(Optional.of(operator));

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(ShipmentStatus.IN_TRANSIT);
        request.setNote("Left the port");

        shipmentService.updateStatus(1L, request, "operator@cargofy.com");

        assertEquals(ShipmentStatus.IN_TRANSIT, shipment.getStatus());
        ArgumentCaptor<StatusHistory> captor = ArgumentCaptor.forClass(StatusHistory.class);
        verify(statusHistoryRepository).save(captor.capture());
        assertEquals(ShipmentStatus.IN_TRANSIT, captor.getValue().getStatus());
        assertEquals("Left the port", captor.getValue().getNote());
        assertEquals(operator, captor.getValue().getUpdatedBy());
    }

    @Test
    void updateStatus_unknownShipmentThrows() {
        when(shipmentRepository.findById(99L)).thenReturn(Optional.empty());

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setStatus(ShipmentStatus.CUSTOMS);

        assertThrows(ResourceNotFoundException.class,
                () -> shipmentService.updateStatus(99L, request, "operator@cargofy.com"));
        verify(statusHistoryRepository, never()).save(any());
    }
}
