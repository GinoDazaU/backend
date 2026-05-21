package com.workpool.reservation;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Async
    public void sendStatusChangeEmail(Reservation reservation) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(reservation.getUser().getEmail());
            message.setSubject("Actualización de reserva - Workpool");
            message.setText(
                    "Hola " + reservation.getUser().getFirstName() + ",\n\n" +
                            "Tu reserva ha cambiado de estado.\n\n" +
                            "Espacio: " + reservation.getOffice().getName() + "\n" +
                            "Fecha: " + reservation.getBeginDate().toLocalDate() + "\n" +
                            "Horario: " + reservation.getBeginDate().toLocalTime() + " - " + reservation.getEndDate().toLocalTime() + "\n" +
                            "Estado: " + reservation.getReservationStatus().getStatusName() + "\n\n" +
                            "Para consultas, contacta a Workpool por WhatsApp.\n\n" +
                            "Workpool"
            );
            mailSender.send(message);
        } catch (Exception e) {
            // RN-091: registrar fallo, no interrumpir operación
            // TODO: registrar en tabla de notificaciones fallidas
        }
    }
}