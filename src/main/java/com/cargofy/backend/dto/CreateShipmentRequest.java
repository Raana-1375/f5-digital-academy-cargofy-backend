package com.cargofy.backend.dto;

import com.cargofy.backend.model.PackageType;
import com.cargofy.backend.model.TransportMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
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

    @Size(max = 300, message = "Note must be at most 300 characters")
    private String note;

    @NotNull(message = "Transport mode is required")
    private TransportMode transportMode;

    @NotNull(message = "Package type is required")
    private PackageType packageType;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.01", message = "Weight must be greater than 0")
    private BigDecimal weightKg;

    @Size(max = 200, message = "Goods description must be at most 200 characters")
    private String goodsDescription;

    
    private boolean dangerousGoods;

    @Pattern(regexp = "^\\d{6,10}$", message = "HS code must contain 6 to 10 digits")
    private String hsCode;
}