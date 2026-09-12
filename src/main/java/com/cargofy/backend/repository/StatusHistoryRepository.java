package com.cargofy.backend.repository;

import com.cargofy.backend.model.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByShipmentIdOrderByUpdatedDateAsc(Long shipmentId);
}
