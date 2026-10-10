package com.cargofy.backend.service;

import com.cargofy.backend.dto.DashboardSummaryResponse;
import com.cargofy.backend.dto.ShipmentResponse;
import com.cargofy.backend.exception.ResourceNotFoundException;
import com.cargofy.backend.model.Role;
import com.cargofy.backend.model.Shipment;
import com.cargofy.backend.model.ShipmentStatus;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.ShipmentRepository;
import com.cargofy.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;

    public DashboardService(ShipmentRepository shipmentRepository, UserRepository userRepository) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(String currentEmail) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getRole() == Role.CLIENT) {
            Long clientId = user.getId();
            return new DashboardSummaryResponse(
                    shipmentRepository.countByClientId(clientId),
                    shipmentRepository.countByClientIdAndStatus(clientId, ShipmentStatus.PREPARING),
                    shipmentRepository.countByClientIdAndStatus(clientId, ShipmentStatus.CUSTOMS),
                    shipmentRepository.countByClientIdAndStatus(clientId, ShipmentStatus.IN_TRANSIT),
                    shipmentRepository.countByClientIdAndStatus(clientId, ShipmentStatus.DELIVERED),
                    toResponses(shipmentRepository.findTop5ByClientIdOrderByCreatedDateDesc(clientId))
            );
        }

        return new DashboardSummaryResponse(
                shipmentRepository.count(),
                shipmentRepository.countByStatus(ShipmentStatus.PREPARING),
                shipmentRepository.countByStatus(ShipmentStatus.CUSTOMS),
                shipmentRepository.countByStatus(ShipmentStatus.IN_TRANSIT),
                shipmentRepository.countByStatus(ShipmentStatus.DELIVERED),
                toResponses(shipmentRepository.findTop5ByOrderByCreatedDateDesc())
        );
    }

    private List<ShipmentResponse> toResponses(List<Shipment> shipments) {
        return shipments.stream().map(this::toResponse).toList();
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getTrackingNumber(),
                shipment.getOrigin(),
                shipment.getDestination(),
                shipment.getStatus().name(),
                shipment.getCreatedDate(),
                shipment.getEstimatedDelivery(),
                shipment.getClient() != null ? shipment.getClient().getName() : null,
                shipment.getAssignedOperator() != null ? shipment.getAssignedOperator().getName() : null,
                shipment.getNote()
        );
    }
}