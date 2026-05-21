package com.workpool.payment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClient gatewayClient;

    @Transactional
    public Payment processPayment(BigDecimal amountUsd, BigDecimal amountPen,
                                  BigDecimal exchangeRate, String description) {
        Payment payment = new Payment();
        payment.setAmountUsd(amountUsd);
        payment.setAmountPen(amountPen);
        payment.setExchangeRate(exchangeRate);

        PaymentGatewayClient.PaymentResult result = gatewayClient.processPayment(amountPen, description);

        if (result.success()) {
            payment.setStatus("SUCCESS");
            payment.setExternalId(result.externalId());
            payment.setPaymentMethod("CARD");
        } else {
            payment.setStatus("FAILED");
            payment.setErrorMessage(result.errorMessage());
        }

        return paymentRepository.save(payment);
    }
}