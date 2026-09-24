package com.cargofy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDetailResponse {

    private Long id;
    private String trackingNumber;
    private String origin;
    private String destination;
    private String status;
    private LocalDateTime createdDate;
    private LocalDateTime estimatedDelivery;
    private String clientName;
    private String assignedOperatorName;
    private List<StatusHistoryResponse> history;
}