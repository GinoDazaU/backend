package com.workpool.reservation.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class PriceQuoteResponse {

    private final BigDecimal pricePerHour;
    private final int planHours;
    private final long totalHours;
    private final long extraHours;
    private final BigDecimal totalPriceUsd;
    private final BigDecimal exchangeRate;
    private final BigDecimal totalPricePen;

    public PriceQuoteResponse(BigDecimal pricePerHour, int planHours, long totalHours,
                              long extraHours, BigDecimal totalPriceUsd,
                              BigDecimal exchangeRate, BigDecimal totalPricePen) {
        this.pricePerHour = pricePerHour;
        this.planHours = planHours;
        this.totalHours = totalHours;
        this.extraHours = extraHours;
        this.totalPriceUsd = totalPriceUsd;
        this.exchangeRate = exchangeRate;
        this.totalPricePen = totalPricePen;
    }
}