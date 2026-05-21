package com.workpool.user;

import com.workpool.common.exception.AppException;
import com.workpool.user.dto.RolePermissionsResponse;
import com.workpool.user.dto.UpdateRolePermissionsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public List<RolePermissionsResponse> getAllRolesWithPermissions() {
        return roleRepository.findAll().stream()
                .map(RolePermissionsResponse::from)
                .toList();
    }

    public RolePermissionsResponse getRolePermissions(UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Rol no encontrado"));
        return RolePermissionsResponse.from(role);
    }

    @Transactional
    public RolePermissionsResponse updateRolePermissions(UUID roleId, UpdateRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Rol no encontrado"));

        // Admin Principal no se puede modificar
        if (role.getRoleName().equals("ADMINISTRADOR_PRINCIPAL")) {
            throw new AppException(HttpStatus.FORBIDDEN,
                    "Los permisos del Administrador Principal no pueden modificarse");
        }

        List<Permission> permissions = permissionRepository.findAllByIdIn(request.getPermissionIds());

        if (permissions.size() != request.getPermissionIds().size()) {
            throw new AppException(HttpStatus.BAD_REQUEST,
                    "Uno o más permisos no existen");
        }

        role.setPermissions(new HashSet<>(permissions));
        return RolePermissionsResponse.from(roleRepository.save(role));
    }
}