package com.workpool.auth;

import com.workpool.user.User;
import com.workpool.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 3;
    private static final int RESET_WINDOW_MINUTES = 5;

    private final UserRepository userRepository;

    @Transactional
    public void loginFailed(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            OffsetDateTime now = OffsetDateTime.now();

            // si pasaron más de 5 min desde el último fallo, reiniciar contador
            if (user.getLastFailedAt() != null
                    && user.getLastFailedAt().plusMinutes(RESET_WINDOW_MINUTES).isBefore(now)) {
                user.setFailedAttempts(0);
            }

            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            user.setLastFailedAt(now);

            if (attempts >= MAX_ATTEMPTS) {
                user.setLockedUntil(now.plusMinutes(LOCK_MINUTES));
                user.setFailedAttempts(0);
                user.setLastFailedAt(null);
            }

            userRepository.save(user);
        });
    }

    @Transactional
    public void loginSucceeded(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            user.setLastFailedAt(null);
            userRepository.save(user);
        });
    }
}