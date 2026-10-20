package com.cargofy.backend.service;

import com.cargofy.backend.model.PackageType;
import com.cargofy.backend.model.TransportMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceEstimatorTest {

    @Test
    @DisplayName("air freight of 2 pallets weighing 100 kg: 25 + 4.50 x 100 + 15 x 2")
    void estimate_airPallets() {
        BigDecimal price = PriceEstimator.estimate(
                TransportMode.AIR, PackageType.PALLET, 2, new BigDecimal("100"));

        assertEquals(new BigDecimal("505.00"), price);
    }

    @Test
    @DisplayName("sea freight of 10 boxes weighing 250.5 kg: 25 + 0.80 x 250.5 + 3 x 10")
    void estimate_seaBoxes() {
        BigDecimal price = PriceEstimator.estimate(
                TransportMode.SEA, PackageType.BOX, 10, new BigDecimal("250.5"));

        assertEquals(new BigDecimal("255.40"), price);
    }

    @Test
    @DisplayName("road freight of one envelope weighing 0.5 kg: 25 + 1.60 x 0.5 + 1")
    void estimate_roadEnvelope() {
        BigDecimal price = PriceEstimator.estimate(
                TransportMode.ROAD, PackageType.ENVELOPE, 1, new BigDecimal("0.5"));

        assertEquals(new BigDecimal("26.80"), price);
    }

    @Test
    @DisplayName("the result is rounded to two decimals")
    void estimate_roundsToTwoDecimals() {
        BigDecimal price = PriceEstimator.estimate(
                TransportMode.AIR, PackageType.BOX, 1, new BigDecimal("0.333"));

        assertEquals(new BigDecimal("29.50"), price);
    }
}