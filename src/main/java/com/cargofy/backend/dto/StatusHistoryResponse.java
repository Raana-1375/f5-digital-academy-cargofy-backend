package com.cargofy.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryResponse {

    private String status;
    private LocalDateTime updatedDate;
    private String updatedByName;
    private String note;
}