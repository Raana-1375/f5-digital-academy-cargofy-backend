package com.cargofy.backend.controller;

import com.cargofy.backend.dto.CreateShipmentRequest;
import com.cargofy.backend.dto.ShipmentDetailResponse;
import com.cargofy.backend.dto.ShipmentResponse;
import com.cargofy.backend.dto.UpdateStatusRequest;
import com.cargofy.backend.model.ShipmentStatus;
import com.cargofy.backend.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @PostMapping
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        return ResponseEntity.ok(shipmentService.createShipment(request));
    }

    @GetMapping
    public ResponseEntity<List<ShipmentResponse>> listShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(shipmentService.listShipments(status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShipmentDetailResponse> getShipmentDetail(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentService.getShipmentDetail(id));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<ShipmentResponse> updateStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateStatusRequest request,
                                                         Authentication authentication) {
        return ResponseEntity.ok(shipmentService.updateStatus(id, request, authentication.getName()));
    }
}