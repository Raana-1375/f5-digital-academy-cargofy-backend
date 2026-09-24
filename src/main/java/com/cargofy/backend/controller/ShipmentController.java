package com.cargofy.backend.controller;

import com.cargofy.backend.dto.CreateShipmentRequest;
import com.cargofy.backend.dto.ShipmentResponse;
import com.cargofy.backend.model.Shipment;
import com.cargofy.backend.model.ShipmentStatus;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.ShipmentRepository;
import com.cargofy.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    public ShipmentController(ShipmentRepository shipmentRepository, UserRepository userRepository) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        Shipment shipment = new Shipment();
        shipment.setTrackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        shipment.setOrigin(request.getOrigin());
        shipment.setDestination(request.getDestination());
        shipment.setStatus(ShipmentStatus.PREPARING);
        shipment.setCreatedDate(LocalDateTime.now());
        shipment.setEstimatedDelivery(request.getEstimatedDelivery());

        if (request.getClientId() != null) {
            User client = userRepository.findById(request.getClientId())
                    .orElseThrow(() -> new RuntimeException("Client not found"));
            shipment.setClient(client);
        }

        if (request.getAssignedOperatorId() != null) {
            User operator = userRepository.findById(request.getAssignedOperatorId())
                    .orElseThrow(() -> new RuntimeException("Operator not found"));
            shipment.setAssignedOperator(operator);
        }

        shipmentRepository.save(shipment);

        ShipmentResponse response = new ShipmentResponse(
                shipment.getId(),
                shipment.getTrackingNumber(),
                shipment.getOrigin(),
                shipment.getDestination(),
                shipment.getStatus().name(),
                shipment.getCreatedDate(),
                shipment.getEstimatedDelivery(),
                shipment.getClient() != null ? shipment.getClient().getName() : null,
                shipment.getAssignedOperator() != null ? shipment.getAssignedOperator().getName() : null
        );

        return ResponseEntity.ok(response);
    }
}