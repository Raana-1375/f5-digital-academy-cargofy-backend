package com.cargofy.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateShipmentRequest {

    @NotBlank(message = "Origin is required")
    private String origin;

    @NotBlank(message = "Destination is required")
    private String destination;

    private Long clientId;

    private Long assignedOperatorId;

    private LocalDateTime estimatedDelivery;

    private String note;
}