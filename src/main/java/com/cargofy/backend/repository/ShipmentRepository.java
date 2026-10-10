package com.cargofy.backend.repository;

import com.cargofy.backend.model.Shipment;
import com.cargofy.backend.model.ShipmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByTrackingNumber(String trackingNumber);
    Page<Shipment> findByStatus(ShipmentStatus status, Pageable pageable);
    
    long countByStatus(ShipmentStatus status);

    long countByClientId(Long clientId);

    long countByClientIdAndStatus(Long clientId, ShipmentStatus status);

    List<Shipment> findTop5ByOrderByCreatedDateDesc();

    List<Shipment> findTop5ByClientIdOrderByCreatedDateDesc(Long clientId);
}