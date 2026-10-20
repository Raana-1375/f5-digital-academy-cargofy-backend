package com.cargofy.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String trackingNumber;

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipmentStatus status;

    private LocalDateTime createdDate;

    private LocalDateTime estimatedDelivery;

    @Column(length = 300)
    private String note;

    // Cargo details. Nullable in the database so shipments created before
    // these columns existed stay valid; the API requires them for new shipments.
    @Enumerated(EnumType.STRING)
    private TransportMode transportMode;

    @Enumerated(EnumType.STRING)
    private PackageType packageType;

    private Integer quantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal weightKg;
    
    // Dangerous goods flag; null on shipments created before this field existed.
    private Boolean dangerousGoods;

    // Harmonized System (customs tariff) code, 6 to 10 digits.
    @Column(length = 10)
    private String hsCode;

    @Column(length = 200)
    private String goodsDescription;

    // Indicative price calculated with the demo tariff when the shipment is created.
    @Column(precision = 10, scale = 2)
    private BigDecimal estimatedPrice;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private User client;

    @ManyToOne
    @JoinColumn(name = "assigned_operator_id")
    private User assignedOperator;
}