package com.workpool.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthEmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public void sendVerificationEmail(com.workpool.user.User user, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Verifica tu correo - Workpool");
        message.setText(
                "Hola " + user.getFirstName() + ",\n\n" +
                        "Para activar tu cuenta haz clic en el siguiente enlace:\n" +
                        link + "\n\n" +
                        "Este enlace expira en 15 minutos.\n\n" +
                        "Si no creaste esta cuenta, ignora este correo.\n\n" +
                        "Workpool"
        );

        mailSender.send(message);
    }

    public void sendPasswordResetEmail(com.workpool.user.User user, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Recupera tu contraseña - Workpool");
        message.setText(
                "Hola " + user.getFirstName() + ",\n\n" +
                        "Para restablecer tu contraseña haz clic en el siguiente enlace:\n" +
                        link + "\n\n" +
                        "Este enlace expira en 15 minutos.\n\n" +
                        "Si no solicitaste esto, ignora este correo.\n\n" +
                        "Workpool"
        );

        mailSender.send(message);
    }
}