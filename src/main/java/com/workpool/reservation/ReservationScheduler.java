package com.workpool.reservation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScheduler {

    private final ReservationService reservationService;

    @Scheduled(fixedRate = 60000) // cada minuto
    public void expirePreReservations() {
        int expired = reservationService.expireOldPreReservations();
        if (expired > 0) {
            log.info("Pre-reservas expiradas: {}", expired);
        }
    }
}