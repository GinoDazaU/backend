package com.workpool.auth;

import com.workpool.auth.dto.LoginRequest;
import com.workpool.auth.dto.RegisterRequest;
import com.workpool.auth.dto.AuthResponse;
import com.workpool.common.exception.AppException;
import com.workpool.user.Role;
import com.workpool.user.RoleRepository;
import com.workpool.user.User;
import com.workpool.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.workpool.auth.dto.ForgotPasswordRequest;
import com.workpool.auth.dto.ResetPasswordRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AuthEmailService authEmailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final LoginAttemptService loginAttemptService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(HttpStatus.CONFLICT, "El correo ya está registrado");
        }

        Role role = roleRepository.findByRoleName("CLIENTE")
                .orElseThrow(() -> new AppException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Rol por defecto no encontrado"));

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setBirthDate(request.getBirthDate());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);
        user.setEnabled(false); // pendiente de verificación (RN-025)

        userRepository.save(user);

        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setUser(user);
        verificationToken.setToken(UUID.randomUUID());
        verificationToken.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
        verificationTokenRepository.save(verificationToken);

        authEmailService.sendVerificationEmail(user, verificationToken.getToken().toString());
    }

    @Transactional
    public void verifyEmail(String token) {
        VerificationToken verificationToken = verificationTokenRepository
                .findByToken(UUID.fromString(token))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Token inválido"));

        if (verificationToken.isExpired()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "El token ha expirado");
        }

        User user = verificationToken.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        verificationTokenRepository.deleteAllByUserId(user.getId());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos"));

        if (user.isLocked()) {
            throw new AppException(HttpStatus.UNAUTHORIZED,
                    "Cuenta bloqueada temporalmente. Intenta en unos minutos.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (Exception e) {
            loginAttemptService.loginFailed(request.getEmail());
            throw new AppException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }

        loginAttemptService.loginSucceeded(request.getEmail());
        String token = jwtService.generateToken(user);
        return AuthResponse.from(user, token);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // RN-044: respuesta genérica aunque el correo no exista
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            passwordResetTokenRepository.deleteAllByUserId(user.getId());

            PasswordResetToken resetToken = new PasswordResetToken();
            resetToken.setUser(user);
            resetToken.setToken(UUID.randomUUID());
            resetToken.setExpiresAt(OffsetDateTime.now().plusMinutes(15));
            passwordResetTokenRepository.save(resetToken);

            authEmailService.sendPasswordResetEmail(user, resetToken.getToken().toString());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByToken(UUID.fromString(request.getToken()))
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "Token inválido"));

        if (resetToken.isExpired()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "El token ha expirado");
        }

        if (resetToken.isUsed()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "El token ya fue utilizado");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
    }
}