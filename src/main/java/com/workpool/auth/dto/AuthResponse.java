package com.workpool.auth.dto;

import com.workpool.user.User;
import lombok.Getter;

import java.util.UUID;

@Getter
public class AuthResponse {

    private final UUID userId;
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String role;
    private final String token;

    private AuthResponse(UUID userId, String email, String firstName,
                         String lastName, String role, String token) {
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.token = token;
    }

    public static AuthResponse from(User user, String token) {
        return new AuthResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole().getRoleName(),
                token
        );
    }
}