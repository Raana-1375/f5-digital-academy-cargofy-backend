package com.cargofy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentResponse {

    private Long id;
    private String trackingNumber;
    private String origin;
    private String destination;
    private String status;
    private LocalDateTime createdDate;
    private LocalDateTime estimatedDelivery;
    private String clientName;
    private String assignedOperatorName;
    private String note;
    private String transportMode;
    private String packageType;
    private Integer quantity;
    private BigDecimal weightKg;
    private String goodsDescription;
    private BigDecimal estimatedPrice;
}