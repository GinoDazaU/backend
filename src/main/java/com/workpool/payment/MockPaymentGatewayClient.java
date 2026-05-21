package com.workpool.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentGatewayClient implements PaymentGatewayClient {

    @Override
    public PaymentResult processPayment(BigDecimal amountPen, String description) {
        // Mock: siempre exitoso. Reemplazar con OpenPay real después.
        return new PaymentResult(
                true,
                "MOCK-" + UUID.randomUUID().toString().substring(0, 8),
                null
        );
    }
}