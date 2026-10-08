package com.cargofy.backend.service;

import com.cargofy.backend.dto.CreateShipmentRequest;
import com.cargofy.backend.dto.ShipmentDetailResponse;
import com.cargofy.backend.dto.ShipmentResponse;
import com.cargofy.backend.dto.StatusHistoryResponse;
import com.cargofy.backend.dto.UpdateStatusRequest;
import com.cargofy.backend.exception.ResourceNotFoundException;
import com.cargofy.backend.model.Shipment;
import com.cargofy.backend.model.ShipmentStatus;
import com.cargofy.backend.model.StatusHistory;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.ShipmentRepository;
import com.cargofy.backend.repository.StatusHistoryRepository;
import com.cargofy.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final UserRepository userRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public ShipmentService(ShipmentRepository shipmentRepository,
                           UserRepository userRepository,
                           StatusHistoryRepository statusHistoryRepository) {
        this.shipmentRepository = shipmentRepository;
        this.userRepository = userRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        Shipment shipment = new Shipment();
        shipment.setTrackingNumber("TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        shipment.setOrigin(request.getOrigin());
        shipment.setDestination(request.getDestination());
        shipment.setStatus(ShipmentStatus.PREPARING);
        shipment.setCreatedDate(LocalDateTime.now());
        shipment.setEstimatedDelivery(request.getEstimatedDelivery());

        if (request.getClientId() != null) {
            User client = userRepository.findById(request.getClientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
            shipment.setClient(client);
        }

        if (request.getAssignedOperatorId() != null) {
            User operator = userRepository.findById(request.getAssignedOperatorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Operator not found"));
            shipment.setAssignedOperator(operator);
        }

        shipmentRepository.save(shipment);
        return toShipmentResponse(shipment);
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> listShipments(ShipmentStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Shipment> shipments = (status != null)
                ? shipmentRepository.findByStatus(status, pageable)
                : shipmentRepository.findAll(pageable);

        return shipments.getContent().stream()
                .map(this::toShipmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShipmentDetailResponse getShipmentDetail(Long id) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        List<StatusHistoryResponse> historyResponse = statusHistoryRepository
                .findByShipmentIdOrderByUpdatedDateAsc(id)
                .stream()
                .map(h -> new StatusHistoryResponse(
                        h.getStatus().name(),
                        h.getUpdatedDate(),
                        h.getUpdatedBy() != null ? h.getUpdatedBy().getName() : null,
                        h.getNote()
                ))
                .toList();

        return new ShipmentDetailResponse(
                shipment.getId(),
                shipment.getTrackingNumber(),
                shipment.getOrigin(),
                shipment.getDestination(),
                shipment.getStatus().name(),
                shipment.getCreatedDate(),
                shipment.getEstimatedDelivery(),
                shipment.getClient() != null ? shipment.getClient().getName() : null,
                shipment.getAssignedOperator() != null ? shipment.getAssignedOperator().getName() : null,
                historyResponse
        );
    }

    @Transactional
    public ShipmentResponse updateStatus(Long id, UpdateStatusRequest request, String currentEmail) {
        Shipment shipment = shipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found"));

        shipment.setStatus(request.getStatus());
        shipmentRepository.save(shipment);

        User updatedBy = userRepository.findByEmail(currentEmail).orElse(null);

        StatusHistory history = new StatusHistory();
        history.setShipment(shipment);
        history.setStatus(request.getStatus());
        history.setUpdatedDate(LocalDateTime.now());
        history.setUpdatedBy(updatedBy);
        history.setNote(request.getNote());
        statusHistoryRepository.save(history);

        return toShipmentResponse(shipment);
    }

       private ShipmentResponse toShipmentResponse(Shipment shipment) {
        return new ShipmentResponse(
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
    }
}