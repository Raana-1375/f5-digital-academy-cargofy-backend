package com.cargofy.backend.dto;

import com.cargofy.backend.model.ShipmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private ShipmentStatus status;

    private String note;
}
