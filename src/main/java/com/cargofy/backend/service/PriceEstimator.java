package com.cargofy.backend.service;

import com.cargofy.backend.model.PackageType;
import com.cargofy.backend.model.TransportMode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indicative price calculator based on a fixed demo tariff:
 * base fee + (rate per kg of the transport mode x weight)
 * + (handling fee of the package type x quantity). Amounts are in EUR.
 * A real system would query carrier tariffs and take the distance into account.
 */
public final class PriceEstimator {

    private static final BigDecimal BASE_FEE = new BigDecimal("25.00");

    private PriceEstimator() {
    }

    public static BigDecimal estimate(TransportMode mode, PackageType packageType,
                                      int quantity, BigDecimal weightKg) {
        BigDecimal ratePerKg = switch (mode) {
            case AIR -> new BigDecimal("4.50");
            case SEA -> new BigDecimal("0.80");
            case ROAD -> new BigDecimal("1.60");
        };
        BigDecimal handlingPerUnit = switch (packageType) {
            case PALLET -> new BigDecimal("15.00");
            case BOX -> new BigDecimal("3.00");
            case ENVELOPE -> new BigDecimal("1.00");
        };

        return BASE_FEE
                .add(ratePerKg.multiply(weightKg))
                .add(handlingPerUnit.multiply(BigDecimal.valueOf(quantity)))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
