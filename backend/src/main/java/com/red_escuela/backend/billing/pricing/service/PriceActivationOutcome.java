package com.red_escuela.backend.billing.pricing.service;

public record PriceActivationOutcome(int pricesActivated, int versionsActivated, int pricesFailed) {
}
