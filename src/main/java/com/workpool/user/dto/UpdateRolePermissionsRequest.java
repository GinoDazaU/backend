package com.workpool.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Set;
import java.util.UUID;

@Getter
public class UpdateRolePermissionsRequest {

    @NotNull(message = "Los IDs de permisos son obligatorios")
    private Set<UUID> permissionIds;
}