package com.workpool.payment;

import java.math.BigDecimal;

public interface PaymentGatewayClient {

    PaymentResult processPayment(BigDecimal amountPen, String description);

    record PaymentResult(boolean success, String externalId, String errorMessage) {}
}