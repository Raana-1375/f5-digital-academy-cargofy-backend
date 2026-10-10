package com.cargofy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryResponse {

    private long total;
    private long preparing;
    private long customs;
    private long inTransit;
    private long delivered;
    private List<ShipmentResponse> recentShipments;
}
